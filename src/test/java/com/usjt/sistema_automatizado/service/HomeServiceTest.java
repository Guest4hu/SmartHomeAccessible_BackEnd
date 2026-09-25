package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.HomeRequest;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import com.usjt.sistema_automatizado.repository.HomeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private HomeRepository homeRepository;

    @Mock
    private HomeMemberRepository homeMemberRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private com.usjt.sistema_automatizado.mapper.HomeMapper homeMapper; // <-- ADICIONE ESTA LINHA

    @InjectMocks
    private HomeService homeService;

    @Test
    void createHome_DeveCriarCasaEVincularCriadorComoAdmin() {
        // 1. Arrange (Preparar os dados e os Mocks)
        Long userId = 1L;
        HomeRequest request = new HomeRequest("Minha Casa Inteligente");

        AppUser mockUser = new AppUser();
        mockUser.setId(userId);

        Home mockHome = new Home();
        mockHome.setId(10L);
        mockHome.setName(request.name());

        // Ensinar os mocks a responder
// Ensinar os mocks a responder de forma flexível
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        lenient().when(homeMapper.toEntity(any(HomeRequest.class))).thenReturn(mockHome);
        lenient().when(homeRepository.save(any(Home.class))).thenReturn(mockHome);

        // 2. Act (Executar o método real)
        homeService.createHome(request, userId);

        // 3. Assert (Verificar se a regra de negócio foi cumprida)
        ArgumentCaptor<HomeMember> memberCaptor = ArgumentCaptor.forClass(HomeMember.class);

        // Verifica se o save do HomeMember foi chamado exatamente 1 vez e captura o que foi salvo
        verify(homeMemberRepository, times(1)).save(memberCaptor.capture());

        HomeMember savedMember = memberCaptor.getValue();

        // A regra de ouro: O criador tem de ser ADMIN!
        assertEquals(HomeRole.ADMIN, savedMember.getRole(), "O criador da casa deve ser ADMIN");
        assertEquals(mockUser, savedMember.getUser());
        assertEquals(mockHome, savedMember.getHome());
    }
}