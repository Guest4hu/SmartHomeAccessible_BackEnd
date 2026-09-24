package com.usjt.sistema_automatizado.mapper;

import com.usjt.sistema_automatizado.dto.request.HomeRequest;
import com.usjt.sistema_automatizado.dto.response.HomeResponse;
import com.usjt.sistema_automatizado.model.entity.Home;
import org.springframework.stereotype.Component;

@Component
public class HomeMapper {

    public Home toEntity(HomeRequest request) {
        if (request == null) {
            return null;
        }

        Home home = new Home();
        home.setName(request.name());

        return home;
    }

    public HomeResponse toResponse(Home entity) {
        if (entity == null) {
            return null;
        }

        return new HomeResponse(
                entity.getId(),
                entity.getName(),
                entity.getCreatedAt()
        );
    }
}