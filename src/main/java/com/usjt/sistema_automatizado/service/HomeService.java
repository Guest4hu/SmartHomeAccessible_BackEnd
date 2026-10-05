package com.usjt.sistema_automatizado.service;

import com.usjt.sistema_automatizado.dto.request.HomeMemberRequest;
import com.usjt.sistema_automatizado.dto.request.HomeRequest;
import com.usjt.sistema_automatizado.dto.response.HomeMemberResponse;
import com.usjt.sistema_automatizado.dto.response.HomeResponse;
import com.usjt.sistema_automatizado.mapper.HomeMapper;
import com.usjt.sistema_automatizado.model.entity.AppUser;
import com.usjt.sistema_automatizado.model.entity.Home;
import com.usjt.sistema_automatizado.model.entity.HomeMember;
import com.usjt.sistema_automatizado.model.enums.HomeRole;
import com.usjt.sistema_automatizado.repository.AppUserRepository;
import com.usjt.sistema_automatizado.repository.HomeMemberRepository;
import com.usjt.sistema_automatizado.repository.HomeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Serviço responsável pelo ciclo de vida das residências e pela gestão de moradores e papéis de acesso.
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final HomeRepository homeRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final AppUserRepository appUserRepository;
    private final HomeMapper homeMapper;

    /**
     * Cria uma nova residência e vincula imediatamente o usuário criador como {@link HomeRole#ADMIN}.
     *
     * @param request dados da residência (nome)
     * @param creatorUserId identificador do usuário criador
     * @return DTO com os dados da casa criada
     * @throws EntityNotFoundException se o usuário criador não existir
     */
    @Transactional
    public HomeResponse createHome(HomeRequest request, Long creatorUserId) {
        // 1. Validar se o utilizador que está a criar a casa realmente existe
        AppUser creator = appUserRepository.findById(creatorUserId)
                .orElseThrow(() -> new EntityNotFoundException("Utilizador não encontrado."));

        // 2. Criar e guardar a entidade Home (a casa nasce "órfã" na sua própria tabela)
        Home home = homeMapper.toEntity(request);
        Home savedHome = homeRepository.save(home);

        // 3. Criar imediatamente o vínculo, tornando o criador no ADMIN da casa
        HomeMember adminMember = new HomeMember();
        adminMember.setHome(savedHome);
        adminMember.setUser(creator);
        adminMember.setRole(HomeRole.ADMIN);

        homeMemberRepository.save(adminMember);

        // 4. Devolver os dados da casa recém-criada
        return homeMapper.toResponse(savedHome);
    }

    @Transactional(readOnly = true)
    public List<HomeResponse> getUserHomes(Long userId) {
        // Busca na tabela intermédia todas as casas a que o utilizador tem acesso
        return homeMemberRepository.findByUserId(userId)
                .stream()
                .map(HomeMember::getHome) // Extrai a casa de dentro do HomeMember
                .map(homeMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HomeMemberResponse> getHomeMembers(Long homeId, Long requesterId) {
        // 1. Verifica se quem está a pedir pertence à casa
        homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));

        // 2. Lista os membros
        return homeMemberRepository.findByHomeId(homeId).stream()
                .map(member -> new HomeMemberResponse(
                        member.getId(),
                        member.getUser().getId(),
                        member.getUser().getName(),
                        member.getUser().getEmail(),
                        member.getRole(),
                        member.getJoinedAt()
                ))
                .toList();
    }

    /**
     * Adiciona um novo usuário como morador (papel FAMILY) a partir do seu endereço de e-mail.
     *
     * <p><b>Controle de Acesso:</b> Permitido unicamente ao {@link HomeRole#ADMIN} da casa.</p>
     *
     * @param homeId identificador da residência
     * @param request dados do convite contendo o e-mail do morador
     * @param requesterId identificador do administrador solicitante
     * @return DTO com os dados do novo membro registrado
     * @throws IllegalArgumentException se o solicitante não for ADMIN ou se o usuário já for membro da casa
     * @throws EntityNotFoundException se o usuário convidado não for encontrado pelo e-mail
     */
    @Transactional
    public HomeMemberResponse addMember(Long homeId, HomeMemberRequest request, Long requesterId) {
        // 1. Autorização: Verifica se o solicitante é o ADMIN da casa
        HomeMember requesterMember = homeMemberRepository.findByHomeIdAndUserId(homeId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("Não tem acesso a esta casa."));

        if (requesterMember.getRole() != HomeRole.ADMIN) {
            throw new IllegalArgumentException("Apenas o ADMIN pode adicionar membros.");
        }

        // 2. Verifica se o e-mail convidado existe no sistema
        AppUser userToAdd = appUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new EntityNotFoundException("Utilizador com este e-mail não encontrado."));

        // 3. Verifica se a pessoa já não está na casa
        if (homeMemberRepository.findByHomeIdAndUserId(homeId, userToAdd.getId()).isPresent()) {
            throw new IllegalArgumentException("Este utilizador já é membro desta casa.");
        }

        // 4. Cria o vínculo do novo membro (sempre como FAMILY)
        HomeMember newMember = new HomeMember();
        newMember.setHome(requesterMember.getHome());
        newMember.setUser(userToAdd);
        newMember.setRole(HomeRole.FAMILY);

        HomeMember savedMember = homeMemberRepository.save(newMember);

        return new HomeMemberResponse(
                savedMember.getId(),
                savedMember.getUser().getId(),
                savedMember.getUser().getName(),
                savedMember.getUser().getEmail(),
                savedMember.getRole(),
                savedMember.getJoinedAt()
        );
    }
}