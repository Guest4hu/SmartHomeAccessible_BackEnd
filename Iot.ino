#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>

// =====================================================================
// IDENTIFICACAO DO DISPOSITIVO
// O deviceId NAO fica mais fixo no codigo: e gerado a partir do MAC
// (unico de fabrica em cada ESP32). Assim o MESMO firmware serve para
// qualquer placa, sem recompilar. O usuario cadastra esse ID no app
// (POST /homes/{homeId}/devices, campo externalId) para associar o
// dispositivo a UMA CASA -- essa associacao vive so no backend/banco,
// o firmware nao precisa saber a qual casa ele pertence.
// =====================================================================
String deviceId;  // preenchido em gerarDeviceId(), no setup()

// Topicos (sem homeId: o external_id ja e globalmente unico)
String TOPIC_TELEMETRY;
String TOPIC_EVENT;
String TOPIC_STATUS;
String TOPIC_CMD;
String TOPIC_CONFIG;

void gerarDeviceId() {
  uint64_t chipId = ESP.getEfuseMac();  // endereco unico gravado de fabrica
  char buf[13];
  snprintf(buf, sizeof(buf), "%04X%08X",
           (uint16_t)(chipId >> 32), (uint32_t)chipId);
  deviceId = "esp32-" + String(buf);

  TOPIC_TELEMETRY = "devices/" + deviceId + "/telemetry";
  TOPIC_EVENT     = "devices/" + deviceId + "/event";
  TOPIC_STATUS    = "devices/" + deviceId + "/status";
  TOPIC_CMD       = "devices/" + deviceId + "/cmd";
  TOPIC_CONFIG    = "devices/" + deviceId + "/config";

  Serial.println("=====================================");
  Serial.print("Device ID (cadastre no app): ");
  Serial.println(deviceId);
  Serial.println("=====================================");
}

// =====================================================================
// WI-FI: rede virtual do proprio Wokwi (so existe dentro da simulacao,
// e aberta e tem acesso real a internet).
// =====================================================================
const char* WIFI_SSID = "Wokwi-GUEST";
const char* WIFI_PASS = "";

// =====================================================================
// BROKER MQTT publico (ponto de encontro entre este simulador e o backend)
// =====================================================================
const char* MQTT_BROKER = "broker.emqx.io";
const int   MQTT_PORT   = 1883;

WiFiClient   espClient;
PubSubClient mqtt(espClient);

// --- Pinos de Hardware ---
#define PIN_BOTAO 13
#define PIN_PIR   14
#define PIN_DHT   27
#define PIN_LDR   34
#define PIN_RGB_R 25
#define PIN_RGB_G 26
#define PIN_RGB_B 4
#define PIN_RELE  32

#define DHTTYPE DHT22
DHT dht(PIN_DHT, DHTTYPE);

// --- Temporizacao Nao-Bloqueante (GLOBAIS) ---
unsigned long ultimaLeituraSensores = 0;
const unsigned long INTERVALO_SENSORES = 5000;      // DHT22 precisa de >= 2s entre leituras

unsigned long ultimoWifiCheck = 0;
const unsigned long INTERVALO_WIFI_CHECK = 10000;   // 10s para checar reconexao Wi-Fi

unsigned long ultimoMqttAttempt = 0;
const unsigned long INTERVALO_MQTT_RETRY = 5000;    // 5s para tentar reconexao MQTT

// --- Debounce do Botao da Campainha ---
int ultimoEstadoBotaoLeitura = HIGH;
int estadoBotaoEstabilizado = HIGH;
unsigned long ultimoTempoDebounceBotao = 0;
const unsigned long DEBOUNCE_DELAY_MS = 50;         // 50ms para filtrar ruidos mecanicos

// --- Cooldown do Sensor de Presenca (PIR) ---
bool presencaAnterior = false;
unsigned long ultimoDisparoPir = 0;
const unsigned long PIR_COOLDOWN_MS = 30000;        // 30s de cooldown contra floods

// --- Estado do Ventilador, Histerese e Manual Override (EdgeClimateAgent) ---
bool ventiladorLigado = false;
unsigned long manualOverrideAte = 0;
const unsigned long MANUAL_OVERRIDE_MS = 15 * 60 * 1000UL; // 15 minutos de override manual

// --- Parametros de automacao dinamicos (atualizados via MQTT /config com validacao AUT-02) ---
double  cfgFanOnAbove  = 28.0;
double  cfgFanOffBelow = 26.0;
int     cfgBellR       = 255;
int     cfgBellG       = 0;
int     cfgBellB       = 0;

// =====================================================================
// MAQUINA DE ESTADOS DO LED RGB (EdgeAlertAgent)
// FSM nao-bloqueante para alertas visuais (Campainha e BLINK_LED).
// Garante conformidade com WCAG 2.3.1 (frequencia de piscada <= 3 Hz).
// Intervalo de 300ms ON / 300ms OFF = 600ms/ciclo = 1.67 Hz.
// =====================================================================
struct LedAlertFsm {
  bool ativo;
  int r;
  int g;
  int b;
  int transicoesRestantes;  // ex: 3 ciclos = 6 transicoes (ON, OFF, ON, OFF, ON, OFF)
  bool faseOn;
  unsigned long ultimaTransicao;
  unsigned long intervaloMs;
};

LedAlertFsm ledAlert = { false, 0, 0, 0, 0, false, 0, 300 };

void aplicarCorBaseLed() {
  analogWrite(PIN_RGB_R, 0);
  analogWrite(PIN_RGB_G, 255);                          // Verde = Sistema Operacional / Normal
  analogWrite(PIN_RGB_B, ventiladorLigado ? 255 : 0);  // Azul = Ventilador Ativo
}

void iniciarBlinkLed(int r, int g, int b, int ciclos = 3, unsigned long intervaloMs = 300) {
  ledAlert.ativo = true;
  ledAlert.r = r;
  ledAlert.g = g;
  ledAlert.b = b;
  ledAlert.transicoesRestantes = ciclos * 2;
  ledAlert.faseOn = true;
  ledAlert.ultimaTransicao = millis();
  ledAlert.intervaloMs = intervaloMs;

  analogWrite(PIN_RGB_R, r);
  analogWrite(PIN_RGB_G, g);
  analogWrite(PIN_RGB_B, b);
}

void atualizarLedFsm(unsigned long agora) {
  if (!ledAlert.ativo) return;

  if (agora - ledAlert.ultimaTransicao >= ledAlert.intervaloMs) {
    ledAlert.ultimaTransicao = agora;
    ledAlert.transicoesRestantes--;

    if (ledAlert.transicoesRestantes <= 0) {
      ledAlert.ativo = false;
      aplicarCorBaseLed();
      return;
    }

    ledAlert.faseOn = !ledAlert.faseOn;
    if (ledAlert.faseOn) {
      analogWrite(PIN_RGB_R, ledAlert.r);
      analogWrite(PIN_RGB_G, ledAlert.g);
      analogWrite(PIN_RGB_B, ledAlert.b);
    } else {
      analogWrite(PIN_RGB_R, 0);
      analogWrite(PIN_RGB_G, 0);
      analogWrite(PIN_RGB_B, 0);
    }
  }
}

// =====================================================================
// CONTROLE DO ATUADOR DE CLIMA COM OVERRIDE MANUAL
// =====================================================================
void ligarVentilador(bool porOverride) {
  digitalWrite(PIN_RELE, HIGH);
  digitalWrite(PIN_RGB_B, HIGH);
  ventiladorLigado = true;
  if (porOverride) {
    manualOverrideAte = millis() + MANUAL_OVERRIDE_MS;
    Serial.println("[CMD] Ventilador LIGADO manualmente. Override ativo por 15 min.");
  }
  if (!ledAlert.ativo) {
    aplicarCorBaseLed();
  }
}

void desligarVentilador(bool porOverride) {
  digitalWrite(PIN_RELE, LOW);
  digitalWrite(PIN_RGB_B, LOW);
  ventiladorLigado = false;
  if (porOverride) {
    manualOverrideAte = millis() + MANUAL_OVERRIDE_MS;
    Serial.println("[CMD] Ventilador DESLIGADO manualmente. Override ativo por 15 min.");
  }
  if (!ledAlert.ativo) {
    aplicarCorBaseLed();
  }
}

// =====================================================================
// CONFIRMACAO PENDENTE (padrao nao-bloqueante para PubSubClient)
// =====================================================================
bool     confirmacaoPendente      = false;
char     confirmacaoTipo[32]      = "";   // ex: "COMMAND_SUCCESS"
char     confirmacaoStatus[16]    = "";   // "DELIVERED" ou "FAILED"
char     confirmacaoAcao[32]      = "";   // ex: "FAN_ON"
char     confirmacaoCorrelId[64]  = "";   // ex: "a1b2c3d4-e5f6-..."

void publicarStatus(const char* estado, bool retido) {
  mqtt.publish(TOPIC_STATUS.c_str(), estado, retido);
}

void agendarConfirmacao(const char* tipo, const char* status, const char* acao, const char* correlId) {
  strncpy(confirmacaoTipo,     tipo,     sizeof(confirmacaoTipo)     - 1);
  strncpy(confirmacaoStatus,   status,   sizeof(confirmacaoStatus)   - 1);
  strncpy(confirmacaoAcao,     acao,     sizeof(confirmacaoAcao)     - 1);
  strncpy(confirmacaoCorrelId, correlId, sizeof(confirmacaoCorrelId) - 1);
  confirmacaoPendente = true;

  Serial.print("[CONFIRM] Confirmacao agendada: tipo=");
  Serial.print(tipo);
  Serial.print(" status=");
  Serial.print(status);
  Serial.print(" acao=");
  Serial.print(acao);
  Serial.print(" correlationId=");
  Serial.println(correlId);
}

void despacharConfirmacaoPendente() {
  if (!confirmacaoPendente) return;

  StaticJsonDocument<320> doc;
  doc["v"]             = 1;
  doc["deviceId"]      = deviceId;
  doc["type"]          = confirmacaoTipo;
  doc["status"]        = confirmacaoStatus;
  doc["action"]        = confirmacaoAcao;
  doc["correlationId"] = confirmacaoCorrelId;

  char buffer[320];
  serializeJson(doc, buffer);

  boolean ok = mqtt.publish(
    TOPIC_EVENT.c_str(),
    (const uint8_t*)buffer,
    (unsigned int)strlen(buffer),
    false   // retain = false
  );

  if (ok) {
    Serial.print("[CONFIRM] Confirmacao publicada com sucesso: ");
    Serial.println(buffer);
    confirmacaoPendente = false;
  } else {
    Serial.println("[CONFIRM][RETRY] Falha ao publicar confirmacao. Tentando novamente no proximo ciclo.");
  }
}

// =====================================================================
// PROCESSAMENTO E VALIDACAO DE CONFIGURACAO (AUT-02)
// Garante histerese valida (fanOnAbove > fanOffBelow), faixas fisicas
// coerentes e valores RGB dentro do espectro 0..255.
// =====================================================================
void processarConfig(const String& payload) {
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, payload);
  if (err) {
    Serial.print("[CONFIG][ERRO] JSON invalido: ");
    Serial.println(err.c_str());
    return;
  }

  double novoFanOnAbove  = doc.containsKey("fanOnAbove")  ? doc["fanOnAbove"].as<double>()  : cfgFanOnAbove;
  double novoFanOffBelow = doc.containsKey("fanOffBelow") ? doc["fanOffBelow"].as<double>() : cfgFanOffBelow;
  int    novoBellR       = doc.containsKey("bellR")       ? doc["bellR"].as<int>()          : cfgBellR;
  int    novoBellG       = doc.containsKey("bellG")       ? doc["bellG"].as<int>()          : cfgBellG;
  int    novoBellB       = doc.containsKey("bellB")       ? doc["bellB"].as<int>()          : cfgBellB;

  // 1. Validacao de Histerese: fanOnAbove deve ser estritamente maior que fanOffBelow
  if (novoFanOnAbove <= novoFanOffBelow) {
    Serial.print("[CONFIG][ERRO] Validacao rejeitada: fanOnAbove (");
    Serial.print(novoFanOnAbove);
    Serial.print(") deve ser estritamente maior que fanOffBelow (");
    Serial.print(novoFanOffBelow);
    Serial.println("). Parametros anteriores preservados.");
    return;
  }

  // 2. Faixa fisica razoavel de operacao residencial (0.0C a 60.0C)
  if (novoFanOffBelow < 0.0 || novoFanOnAbove > 60.0) {
    Serial.println("[CONFIG][ERRO] Validacao rejeitada: temperaturas fora da faixa permitida [0.0, 60.0 C].");
    return;
  }

  // 3. Validacao de valores RGB (0..255)
  if (novoBellR < 0 || novoBellR > 255 ||
      novoBellG < 0 || novoBellG > 255 ||
      novoBellB < 0 || novoBellB > 255) {
    Serial.println("[CONFIG][ERRO] Validacao rejeitada: valores RGB devem estar entre 0 e 255.");
    return;
  }

  // Aplica os parametros validados
  cfgFanOnAbove  = novoFanOnAbove;
  cfgFanOffBelow = novoFanOffBelow;
  cfgBellR       = novoBellR;
  cfgBellG       = novoBellG;
  cfgBellB       = novoBellB;

  Serial.println("[CONFIG] Parametros atualizados e validados com sucesso:");
  Serial.print("  fanOnAbove=" );  Serial.println(cfgFanOnAbove);
  Serial.print("  fanOffBelow=");  Serial.println(cfgFanOffBelow);
  Serial.print("  bellR=");        Serial.println(cfgBellR);
  Serial.print("  bellG=");        Serial.println(cfgBellG);
  Serial.print("  bellB=");        Serial.println(cfgBellB);
}

// =====================================================================
// CALLBACK MQTT (execucao rapida, sem bloqueios)
// =====================================================================
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String msg;
  msg.reserve(length);
  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }

  Serial.println("---------------------------------------------");
  Serial.print("[CMD] Mensagem recebida no topico: ");
  Serial.println(topic);
  Serial.print("[CMD] Payload bruto: ");
  Serial.println(msg);
  Serial.println("---------------------------------------------");

  // Roteamento para configuracao
  if (String(topic) == TOPIC_CONFIG) {
    processarConfig(msg);
    return;
  }

  // Parsing do JSON do comando
  StaticJsonDocument<512> doc;
  DeserializationError err = deserializeJson(doc, msg);
  if (err) {
    Serial.print("[CMD][ERRO] JSON invalido: ");
    Serial.println(err.c_str());
    agendarConfirmacao("COMMAND_FAILED", "FAILED", "INVALID_JSON", "");
    return;
  }

  const char* action  = doc["action"]        | "";
  const char* correlId = doc["correlationId"] | "";

  if (strlen(action) == 0) {
    Serial.println("[CMD][ERRO] Campo 'action' ausente ou vazio.");
    agendarConfirmacao("COMMAND_FAILED", "FAILED", "MISSING_ACTION", correlId);
    return;
  }

  Serial.print("[CMD] Acao recebida: ");
  Serial.print(action);
  Serial.print(" | correlationId: ");
  Serial.println(correlId);

  // Tratamento de payload de firmware/arquivo
  if (doc.containsKey("file")) {
    Serial.print("[FILE] Arquivo recebido para acao '");
    Serial.print(action);
    Serial.println("'. Processando...");
    agendarConfirmacao("FILE_RECEIVED", "DELIVERED", action, correlId);
    return;
  }

  // Despacho de acoes de atuadores
  if (strcmp(action, "FAN_ON") == 0) {
    ligarVentilador(true); // Ligar via override manual
    agendarConfirmacao("COMMAND_SUCCESS", "DELIVERED", action, correlId);

  } else if (strcmp(action, "FAN_OFF") == 0) {
    desligarVentilador(true); // Desligar via override manual
    agendarConfirmacao("COMMAND_SUCCESS", "DELIVERED", action, correlId);

  } else if (strcmp(action, "BLINK_LED") == 0) {
    Serial.println("[CMD] Executando BLINK_LED de forma nao-bloqueante (3 ciclos de 300ms).");
    iniciarBlinkLed(255, 0, 0, 3, 300); // Pisca em vermelho
    agendarConfirmacao("COMMAND_SUCCESS", "DELIVERED", action, correlId);

  } else {
    Serial.print("[CMD][ERRO] Acao desconhecida recebida: '");
    Serial.print(action);
    Serial.println("'. Nenhuma acao executada.");
    agendarConfirmacao("COMMAND_FAILED", "FAILED", action, correlId);
  }
}

// =====================================================================
// RECONEXAO MQTT NAO-BLOQUEANTE COM QoS 1
// =====================================================================
void tentarConectarMqtt() {
  Serial.print("[MQTT] Tentando conectar ao broker MQTT...");
  String clientId = deviceId + "-" + String(random(0xffff), HEX);

  bool ok = mqtt.connect(
      clientId.c_str(),
      NULL, NULL,
      TOPIC_STATUS.c_str(), 1, true,
      "OFFLINE");

  if (ok) {
    Serial.println(" conectado com sucesso!");
    publicarStatus("ONLINE", true);
    mqtt.subscribe(TOPIC_CMD.c_str(), 1);
    mqtt.subscribe(TOPIC_CONFIG.c_str(), 1);
  } else {
    Serial.print(" falhou, rc=");
    Serial.print(mqtt.state());
    Serial.println(" (proxima tentativa em 5s)");
  }
}

// =====================================================================
// PUBLICACAO DE EVENTOS E TELEMETRIA
// =====================================================================
void publicarEvento(const char* tipo) {
  StaticJsonDocument<200> doc;
  doc["v"] = 1;
  doc["deviceId"] = deviceId;
  doc["type"] = tipo;

  char buffer[200];
  serializeJson(doc, buffer);

  boolean ok = mqtt.publish(
    TOPIC_EVENT.c_str(),
    (const uint8_t*)buffer,
    (unsigned int)strlen(buffer),
    false
  );

  if (ok) {
    Serial.print("[MQTT] Evento publicado: ");
    Serial.println(buffer);
  } else {
    Serial.print("[MQTT][ERRO] Falha ao publicar evento: ");
    Serial.println(tipo);
  }
}

void publicarTelemetria(float temperatura, float umidade, int luminosidade) {
  StaticJsonDocument<256> doc;
  doc["v"] = 1;
  doc["deviceId"] = deviceId;
  doc["temperature"] = temperatura;
  doc["humidity"] = umidade;
  doc["luminosity"] = luminosidade;

  char buffer[256];
  serializeJson(doc, buffer);

  bool ok = mqtt.publish(TOPIC_TELEMETRY.c_str(), buffer);
  if (ok) {
    Serial.print("[MQTT] Telemetria publicada: ");
    Serial.println(buffer);
  } else {
    Serial.println("[MQTT][ERRO] Falha ao publicar telemetria (sem conexao).");
  }
}

// =====================================================================
// PROCESSAMENTO DE SENSORES E CONTROLE DE CLIMA
// =====================================================================
void processarBotaoCampainha(unsigned long agora) {
  int leitura = digitalRead(PIN_BOTAO);

  if (leitura != ultimoEstadoBotaoLeitura) {
    ultimoTempoDebounceBotao = agora;
  }
  ultimoEstadoBotaoLeitura = leitura;

  if ((agora - ultimoTempoDebounceBotao) > DEBOUNCE_DELAY_MS) {
    if (leitura != estadoBotaoEstabilizado) {
      estadoBotaoEstabilizado = leitura;
      if (estadoBotaoEstabilizado == LOW) { // Borda de descida (pressionado)
        Serial.println("[ALERTA] Campainha acionada!");
        publicarEvento("DOORBELL");
        iniciarBlinkLed(cfgBellR, cfgBellG, cfgBellB, 3, 300);
      }
    }
  }
}

void processarSensorPresenca(unsigned long agora) {
  bool presencaAtual = (digitalRead(PIN_PIR) == HIGH);

  if (presencaAtual && !presencaAnterior) {
    if (agora - ultimoDisparoPir >= PIR_COOLDOWN_MS || ultimoDisparoPir == 0) {
      Serial.println("[EVENTO] Presenca detectada!");
      publicarEvento("PRESENCE_DETECTED");
      ultimoDisparoPir = agora;
    } else {
      Serial.println("[EVENTO] Presenca detectada ignorada por cooldown.");
    }
  }
  presencaAnterior = presencaAtual;
}

void processarSensoresEClima(unsigned long agora) {
  if (agora - ultimaLeituraSensores < INTERVALO_SENSORES) return;
  ultimaLeituraSensores = agora;

  float temperatura = dht.readTemperature();
  float umidade = dht.readHumidity();
  int luminosidade = analogRead(PIN_LDR);

  if (!isnan(temperatura) && !isnan(umidade)) {
    publicarTelemetria(temperatura, umidade, luminosidade);

    // Histerese climatica com respeito a Override Manual
    bool emOverride = (agora < manualOverrideAte);
    if (!emOverride) {
      if (temperatura > cfgFanOnAbove && !ventiladorLigado) {
        ligarVentilador(false);
        Serial.print("[FAN] Ventilador LIGADO por automacao (temp > ");
        Serial.print(cfgFanOnAbove);
        Serial.println(" C).");
      } else if (temperatura < cfgFanOffBelow && ventiladorLigado) {
        desligarVentilador(false);
        Serial.print("[FAN] Ventilador DESLIGADO por automacao (temp < ");
        Serial.print(cfgFanOffBelow);
        Serial.println(" C).");
      }
    } else {
      Serial.print("[FAN] Override manual ativo. Restam ");
      Serial.print((manualOverrideAte - agora) / 1000);
      Serial.println("s para retornar ao modo automatico.");
    }
  } else {
    Serial.println("[DHT22] Falha na leitura (NaN).");
  }
}

// =====================================================================
// SETUP
// =====================================================================
void setup() {
  Serial.begin(115200);

  gerarDeviceId();

  pinMode(PIN_BOTAO, INPUT_PULLUP);
  pinMode(PIN_PIR,   INPUT);
  pinMode(PIN_RGB_R, OUTPUT);
  pinMode(PIN_RGB_G, OUTPUT);
  pinMode(PIN_RGB_B, OUTPUT);
  pinMode(PIN_RELE,  OUTPUT);

  dht.begin();

  aplicarCorBaseLed();
  digitalWrite(PIN_RELE, LOW);

  // Tentativa inicial de conexao Wi-Fi (maximo 5s para nao travar o boot local)
  Serial.print("Iniciando Wi-Fi...");
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  unsigned long startWifi = millis();
  while (WiFi.status() != WL_CONNECTED && (millis() - startWifi < 5000)) {
    delay(200);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(" conectado!");
    Serial.print("IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println(" conexao Wi-Fi em background.");
  }

  mqtt.setServer(MQTT_BROKER, MQTT_PORT);
  mqtt.setBufferSize(512);
  mqtt.setCallback(mqttCallback);

  Serial.println("Sistema de Acessibilidade Residencial inicializado.");
}

// =====================================================================
// LOOP PRINCIPAL (100% Nao-Bloqueante)
// =====================================================================
void loop() {
  unsigned long agora = millis();

  // 1. Gerenciamento Nao-Bloqueante de Rede (Wi-Fi e MQTT)
  if (WiFi.status() != WL_CONNECTED) {
    if (agora - ultimoWifiCheck >= INTERVALO_WIFI_CHECK) {
      ultimoWifiCheck = agora;
      Serial.println("[WIFI] Reconectando ao Wi-Fi em segundo plano...");
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASS);
    }
  } else {
    if (!mqtt.connected()) {
      if (agora - ultimoMqttAttempt >= INTERVALO_MQTT_RETRY) {
        ultimoMqttAttempt = agora;
        tentarConectarMqtt();
      }
    } else {
      mqtt.loop();
    }
  }

  // 2. Despacho de confirmacoes pendentes (seguro, fora do callback)
  despacharConfirmacaoPendente();

  // 3. FSM do LED RGB (piscar visual sem bloquear o processador)
  atualizarLedFsm(agora);

  // 4. Debounce do Botao da Campainha
  processarBotaoCampainha(agora);

  // 5. Sensor de Presenca com Cooldown
  processarSensorPresenca(agora);

  // 6. Leituras Periodicas de Sensores e Histerese Climatica
  processarSensoresEClima(agora);
}