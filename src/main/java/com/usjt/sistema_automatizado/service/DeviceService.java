package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.DeviceRequest;
import com.usjt.sistema_automatizado.dto.response.DeviceResponse;
import com.usjt.sistema_automatizado.mapper.DeviceMapper;
import com.usjt.sistema_automatizado.model.entity.Device;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.repository.DeviceRepository;
import com.usjt.sistema_automatizado.repository.HomeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // <-- O Lombok cria o construtor invisível.
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final HomeRepository homeRepository;
    private final DeviceMapper deviceMapper;

    @Transactional
    public DeviceResponse createDevice(DeviceRequest request) {
        Home home = homeRepository.findById(request.getHomeId())
                .orElseThrow(() -> new EntityNotFoundException("Casa não encontrada com o ID: " + request.getHomeId()));

        if (deviceRepository.findByExternalId(request.getExternalId()).isPresent()) {
            throw new IllegalArgumentException("Já existe um dispositivo com o identificador externo: " + request.getExternalId());
        }

        Device device = deviceMapper.toEntity(request, home);
        Device savedDevice = deviceRepository.save(device);

        return deviceMapper.toResponse(savedDevice);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> findAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(deviceMapper::toResponse)
                .collect(Collectors.toList());
    }
}