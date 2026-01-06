package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.request.CreateInvitationRequest;
import com.cepsandik.communityservice.dto.request.JoinCommunityRequest;
import com.cepsandik.communityservice.dto.response.CommunityResponse;
import com.cepsandik.communityservice.dto.response.InvitationResponse;
import com.cepsandik.communityservice.entity.Community;
import com.cepsandik.communityservice.entity.CommunityInvitation;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.CommunityVisibility;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.mapper.InvitationMapper;
import com.cepsandik.communityservice.repository.CommunityInvitationRepository;
import com.cepsandik.communityservice.repository.CommunityMemberRepository;
import com.cepsandik.communityservice.repository.CommunityRepository;
import com.cepsandik.communityservice.util.CodeGenerator;
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
class InvitationServiceTest {

    @Mock
    private CommunityInvitationRepository invitationRepository;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private CommunityMemberRepository memberRepository;

    @Mock
    private CodeGenerator codeGenerator;

    @Mock
    private CommunityService communityService;

    @Spy
    private InvitationMapper invitationMapper = new InvitationMapper();

    @InjectMocks
    private InvitationService invitationService;

    private Community testCommunity;
    private CommunityMember testOwner;
    private CommunityMember testMember;
    private CommunityInvitation testInvitation;
    private final String ownerId = "owner-123";
    private final String userId = "user-456";

    @BeforeEach
    void setUp() {
        testCommunity = new Community();
        testCommunity.setId(1L);
        testCommunity.setName("Test Topluluk");
        testCommunity.setVisibility(CommunityVisibility.PUBLIC);
        testCommunity.setOwnerId(ownerId);
        testCommunity.setIsDeleted(false);

        testOwner = new CommunityMember();
        testOwner.setId(1L);
        testOwner.setCommunityId(1L);
        testOwner.setUserId(ownerId);
        testOwner.setRole(MemberRole.OWNER);
        testOwner.setStatus(MemberStatus.APPROVED);

        testMember = new CommunityMember();
        testMember.setId(2L);
        testMember.setCommunityId(1L);
        testMember.setUserId(userId);
        testMember.setRole(MemberRole.MEMBER);
        testMember.setStatus(MemberStatus.APPROVED);

        testInvitation = new CommunityInvitation();
        testInvitation.setId(1L);
        testInvitation.setCommunityId(1L);
        testInvitation.setCode("ABC12345");
        testInvitation.setMaxUses(10);
        testInvitation.setCurrentUses(0);
        testInvitation.setIsActive(true);
        testInvitation.setCreatedBy(ownerId);
        testInvitation.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Davet başarıyla oluşturulmalı")
    void createInvitation_Success() {
        // Given
        CreateInvitationRequest request = new CreateInvitationRequest();
        request.setMaxUses(10);
        request.setExpiresInHours(24);

        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.findByCommunityIdAndUserId(1L, ownerId)).thenReturn(Optional.of(testOwner));
        when(codeGenerator.generateInvitationCode()).thenReturn("NEWCODE1");
        when(invitationRepository.findByCode("NEWCODE1")).thenReturn(Optional.empty());
        when(invitationRepository.save(any(CommunityInvitation.class))).thenAnswer(invocation -> {
            CommunityInvitation inv = invocation.getArgument(0);
            inv.setId(1L);
            inv.setCreatedAt(LocalDateTime.now());
            return inv;
        });

        // When
        InvitationResponse response = invitationService.createInvitation(1L, request, ownerId);

        // Then
        assertNotNull(response);
        assertEquals("NEWCODE1", response.getCode());
        assertEquals(10, response.getMaxUses());
        verify(invitationRepository).save(any(CommunityInvitation.class));
    }

    @Test
    @DisplayName("Normal üye davet oluşturamamalı")
    void createInvitation_NotAdminOrOwner_ThrowsException() {
        // Given
        CreateInvitationRequest request = new CreateInvitationRequest();

        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.findByCommunityIdAndUserId(1L, userId)).thenReturn(Optional.of(testMember));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> invitationService.createInvitation(1L, request, userId));

        assertEquals("Davet oluşturma yetkiniz yok", exception.getMessage());
    }

    @Test
    @DisplayName("Kullanıcı davet koduyla topluluğa katılmalı")
    void joinCommunity_Success() {
        // Given
        JoinCommunityRequest request = new JoinCommunityRequest();
        request.setCode("ABC12345");

        CommunityResponse mockResponse = CommunityResponse.builder()
                .id(1L)
                .name("Test Topluluk")
                .build();

        when(invitationRepository.findByCode("ABC12345")).thenReturn(Optional.of(testInvitation));
        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.existsByCommunityIdAndUserId(1L, userId)).thenReturn(false);
        when(memberRepository.save(any(CommunityMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(CommunityInvitation.class))).thenReturn(testInvitation);
        when(communityService.getCommunityById(1L, userId)).thenReturn(mockResponse);

        // When
        CommunityResponse response = invitationService.joinCommunity(request, userId);

        // Then
        assertNotNull(response);
        assertEquals(1L, response.getId());
        verify(memberRepository)
                .save(argThat(m -> m.getRole() == MemberRole.MEMBER && m.getStatus() == MemberStatus.APPROVED));
    }

    @Test
    @DisplayName("Geçersiz davet kodu hata fırlatmalı")
    void joinCommunity_InvalidCode_ThrowsException() {
        // Given
        JoinCommunityRequest request = new JoinCommunityRequest();
        request.setCode("INVALID");

        when(invitationRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> invitationService.joinCommunity(request, userId));

        assertEquals("Geçersiz davet kodu", exception.getMessage());
    }

    @Test
    @DisplayName("Pasif davet kullanılamamalı")
    void joinCommunity_InactiveInvitation_ThrowsException() {
        // Given
        JoinCommunityRequest request = new JoinCommunityRequest();
        request.setCode("ABC12345");

        testInvitation.setIsActive(false);

        when(invitationRepository.findByCode("ABC12345")).thenReturn(Optional.of(testInvitation));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> invitationService.joinCommunity(request, userId));

        assertEquals("Bu davet artık aktif değil", exception.getMessage());
    }

    @Test
    @DisplayName("Süresi dolmuş davet kullanılamamalı")
    void joinCommunity_ExpiredInvitation_ThrowsException() {
        // Given
        JoinCommunityRequest request = new JoinCommunityRequest();
        request.setCode("ABC12345");

        testInvitation.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(invitationRepository.findByCode("ABC12345")).thenReturn(Optional.of(testInvitation));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> invitationService.joinCommunity(request, userId));

        assertEquals("Bu davetin süresi dolmuş", exception.getMessage());
    }

    @Test
    @DisplayName("Maksimum kullanıma ulaşmış davet kullanılamamalı")
    void joinCommunity_MaxUsesReached_ThrowsException() {
        // Given
        JoinCommunityRequest request = new JoinCommunityRequest();
        request.setCode("ABC12345");

        testInvitation.setMaxUses(10);
        testInvitation.setCurrentUses(10);

        when(invitationRepository.findByCode("ABC12345")).thenReturn(Optional.of(testInvitation));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> invitationService.joinCommunity(request, userId));

        assertEquals("Bu davet maksimum kullanım sayısına ulaşmış", exception.getMessage());
    }

    @Test
    @DisplayName("Zaten üye olan kullanıcı tekrar katılamamalı")
    void joinCommunity_AlreadyMember_ThrowsException() {
        // Given
        JoinCommunityRequest request = new JoinCommunityRequest();
        request.setCode("ABC12345");

        when(invitationRepository.findByCode("ABC12345")).thenReturn(Optional.of(testInvitation));
        when(communityRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(testCommunity));
        when(memberRepository.existsByCommunityIdAndUserId(1L, userId)).thenReturn(true);

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> invitationService.joinCommunity(request, userId));

        assertEquals("Bu topluluğun zaten üyesisiniz", exception.getMessage());
    }

    @Test
    @DisplayName("Davet başarıyla iptal edilmeli")
    void deactivateInvitation_Success() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(1L, ownerId)).thenReturn(Optional.of(testOwner));
        when(invitationRepository.findById(1L)).thenReturn(Optional.of(testInvitation));
        when(invitationRepository.save(any(CommunityInvitation.class))).thenReturn(testInvitation);

        // When
        invitationService.deactivateInvitation(1L, 1L, ownerId);

        // Then
        verify(invitationRepository).save(argThat(inv -> !inv.getIsActive()));
    }
}
