package com.usjt.sistema_automatizado.controller;

import com.usjt.sistema_automatizado.dto.request.HomeMemberRequest;
import com.usjt.sistema_automatizado.dto.request.HomeRequest;
import com.usjt.sistema_automatizado.dto.response.HomeMemberResponse;
import com.usjt.sistema_automatizado.dto.response.HomeResponse;
import com.usjt.sistema_automatizado.service.HomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/homes")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @PostMapping
    public ResponseEntity<HomeResponse> createHome(
            @Valid @RequestBody HomeRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        HomeResponse response = homeService.createHome(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<HomeResponse>> getUserHomes(
            @AuthenticationPrincipal Long userId
    ) {
        List<HomeResponse> responses = homeService.getUserHomes(userId);
        return ResponseEntity.ok(responses);
    }
    @GetMapping("/{homeId}/members")
    public ResponseEntity<List<HomeMemberResponse>> getHomeMembers(
            @PathVariable Long homeId,
            @AuthenticationPrincipal Long requesterId
    ) {
        List<HomeMemberResponse> responses = homeService.getHomeMembers(homeId, requesterId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{homeId}/members")
    public ResponseEntity<HomeMemberResponse> addMember(
            @PathVariable Long homeId,
            @RequestBody @Valid HomeMemberRequest request,
            @AuthenticationPrincipal Long requesterId) {

        HomeMemberResponse response = homeService.addMember(homeId, request, requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}