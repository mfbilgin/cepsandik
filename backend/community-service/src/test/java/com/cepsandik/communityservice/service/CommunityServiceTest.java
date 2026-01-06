package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.request.CreateCommunityRequest;
import com.cepsandik.communityservice.dto.request.UpdateCommunityRequest;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.CommunityVisibility;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.mapper.CommunityMapper;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
import com.cepsandik.communityservice.repository.CommunityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private CommunityMemberRepository memberRepository;

    @Spy
    private CommunityMapper communityMapper = new CommunityMapper();

    @InjectMocks
    private CommunityService communityService;

    private Community testCommunity;
    private CommunityMember testMember;
    private final String testUserId = "user-123";

    @BeforeEach
    void setUp() {
        testCommunity = new Community();
        testCommunity.setId(1L);
        testCommunity.setName("Test Topluluk");
        testCommunity.setDescription("Test açıklama");
        testCommunity.setVisibility(CommunityVisibility.PUBLIC);
        testCommunity.setOwnerId(testUserId);
        testCommunity.setIsDeleted(false);
        testCommunity.setCreatedAt(LocalDateTime.now());
        testCommunity.setUpdatedAt(LocalDateTime.now());

        testMember = new CommunityMember();
        testMember.setId(1L);
        testMember.setCommunityId(1L);
        testMember.setUserId(testUserId);
        testMember.setRole(MemberRole.OWNER);
        testMember.setStatus(MemberStatus.APPROVED);
        testMember.setJoinedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Topluluk başarıyla oluşturulmalı")
    void createCommunity_Success() {
        // Given
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setName("Yeni Topluluk");
        request.setDescription("Açıklama");
        request.setVisibility(CommunityVisibility.PUBLIC);

        when(communityRepository.existsByNameAndOwnerIdAndIsDeletedFalse(anyString(), anyString()))
                .thenReturn(false);
        when(communityRepository.save(any(Community.class))).thenAnswer(invocation -> {
            Community c = invocation.getArgument(0);
            c.setId(1L);
            c.setCreatedAt(LocalDateTime.now());
            c.setUpdatedAt(LocalDateTime.now());
            return c;
        });
        when(memberRepository.save(any(CommunityMember.class))).thenAnswer(invocation -> {
            CommunityMember m = invocation.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(memberRepository.countByCommunityIdAndStatus(anyLong(), eq(MemberStatus.APPROVED)))
                .thenReturn(1L);
        when(memberRepository.findByCommunityIdAndUserId(anyLong(), anyString()))
                .thenReturn(Optional.of(testMember));

        // When
        CommunityResponse response = communityService.createCommunity(request, testUserId);

        // Then
        assertNotNull(response);
        assertEquals("Yeni Topluluk", response.getName());
        assertEquals(1L, response.getMemberCount());
        assertEquals(MemberRole.OWNER, response.getUserRole());

        verify(communityRepository).save(any(Community.class));
        verify(memberRepository).save(any(CommunityMember.class));
    }

    @Test
    @DisplayName("Aynı isimde topluluk varsa hata fırlatmalı")
    void createCommunity_DuplicateName_ThrowsException() {
        // Given
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setName("Mevcut Topluluk");
        request.setVisibility(CommunityVisibility.PUBLIC);

        when(communityRepository.existsByNameAndOwnerIdAndIsDeletedFalse(anyString(), anyString()))
                .thenReturn(true);

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> communityService.createCommunity(request, testUserId));

        assertEquals("Bu isimde bir topluluğunuz zaten var", exception.getMessage());
        verify(communityRepository, never()).save(any());
    }

    @Test
    @DisplayName("Topluluk detayları başarıyla getirilmeli")
    void getCommunityById_Success() {
        // Given
        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.existsByCommunityIdAndUserId(1L, testUserId)).thenReturn(true);
        when(memberRepository.countByCommunityIdAndStatus(1L, MemberStatus.APPROVED)).thenReturn(5L);
        when(memberRepository.findByCommunityIdAndUserId(1L, testUserId)).thenReturn(Optional.of(testMember));

        // When
        CommunityResponse response = communityService.getCommunityById(1L, testUserId);

        // Then
        assertNotNull(response);
        assertEquals("Test Topluluk", response.getName());
        assertEquals(5L, response.getMemberCount());
    }

    @Test
    @DisplayName("Üye değilse topluluk detaylarına erişim engellenmeli")
    void getCommunityById_NotMember_ThrowsException() {
        // Given
        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.existsByCommunityIdAndUserId(1L, "other-user")).thenReturn(false);

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> communityService.getCommunityById(1L, "other-user"));

        assertEquals("Bu topluluğun üyesi değilsiniz", exception.getMessage());
    }

    @Test
    @DisplayName("Topluluk bulunamazsa hata fırlatmalı")
    void getCommunityById_NotFound_ThrowsException() {
        // Given
        when(communityRepository.findByIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> communityService.getCommunityById(999L, testUserId));

        assertEquals("Topluluk bulunamadı", exception.getMessage());
    }

    @Test
    @DisplayName("Topluluk başarıyla güncellenmeli")
    void updateCommunity_Success() {
        // Given
        UpdateCommunityRequest request = new UpdateCommunityRequest();
        request.setName("Güncellenmiş Ad");
        request.setDescription("Güncellenmiş açıklama");

        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.findByCommunityIdAndUserId(1L, testUserId)).thenReturn(Optional.of(testMember));
        when(communityRepository.save(any(Community.class))).thenReturn(testCommunity);
        when(memberRepository.countByCommunityIdAndStatus(1L, MemberStatus.APPROVED)).thenReturn(1L);

        // When
        CommunityResponse response = communityService.updateCommunity(1L, request, testUserId);

        // Then
        assertNotNull(response);
        verify(communityRepository).save(any(Community.class));
    }

    @Test
    @DisplayName("Yetkisiz kullanıcı güncelleme yapamamalı")
    void updateCommunity_Unauthorized_ThrowsException() {
        // Given
        UpdateCommunityRequest request = new UpdateCommunityRequest();
        request.setName("Güncellenmiş Ad");

        CommunityMember normalMember = new CommunityMember();
        normalMember.setRole(MemberRole.MEMBER);

        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.findByCommunityIdAndUserId(1L, "normal-user"))
                .thenReturn(Optional.of(normalMember));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> communityService.updateCommunity(1L, request, "normal-user"));

        assertEquals("Topluluk güncelleme yetkiniz yok", exception.getMessage());
    }

    @Test
    @DisplayName("Topluluk başarıyla silinmeli (soft delete)")
    void deleteCommunity_Success() {
        // Given
        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(communityRepository.save(any(Community.class))).thenReturn(testCommunity);

        // When
        communityService.deleteCommunity(1L, testUserId);

        // Then
        verify(communityRepository).save(argThat(c -> c.getIsDeleted()));
    }

    @Test
    @DisplayName("Sahip olmayan kullanıcı silme yapamamalı")
    void deleteCommunity_NotOwner_ThrowsException() {
        // Given
        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> communityService.deleteCommunity(1L, "other-user"));

        assertEquals("Bu topluluğu silme yetkiniz yok", exception.getMessage());
        verify(communityRepository, never()).save(any());
    }
}
