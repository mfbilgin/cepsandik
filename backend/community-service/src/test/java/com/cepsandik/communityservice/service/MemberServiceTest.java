package com.cepsandik.communityservice.service;

import com.cepsandik.communityservice.dto.request.UpdateMemberRoleRequest;
import com.cepsandik.communityservice.dto.response.MemberResponse;
import com.cepsandik.communityservice.dto.response.PageResponse;
import com.cepsandik.communityservice.entity.CommunityMember;
import com.cepsandik.communityservice.enums.MemberRole;
import com.cepsandik.communityservice.enums.MemberStatus;
import com.cepsandik.communityservice.exception.ApiException;
import com.cepsandik.communityservice.mapper.MemberMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private CommunityMemberRepository memberRepository;

    @Mock
    private CommunityRepository communityRepository;

    @Spy
    private MemberMapper memberMapper = new MemberMapper();

    @InjectMocks
    private MemberService memberService;

    private CommunityMember owner;
    private CommunityMember admin;
    private CommunityMember member;
    private CommunityMember pendingMember;
    private final String ownerId = "owner-123";
    private final String adminId = "admin-456";
    private final String memberId = "member-789";
    private final Long communityId = 1L;

    @BeforeEach
    void setUp() {
        owner = new CommunityMember();
        owner.setId(1L);
        owner.setCommunityId(communityId);
        owner.setUserId(ownerId);
        owner.setRole(MemberRole.OWNER);
        owner.setStatus(MemberStatus.APPROVED);
        owner.setJoinedAt(LocalDateTime.now());

        admin = new CommunityMember();
        admin.setId(2L);
        admin.setCommunityId(communityId);
        admin.setUserId(adminId);
        admin.setRole(MemberRole.ADMIN);
        admin.setStatus(MemberStatus.APPROVED);
        admin.setJoinedAt(LocalDateTime.now());

        member = new CommunityMember();
        member.setId(3L);
        member.setCommunityId(communityId);
        member.setUserId(memberId);
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.APPROVED);
        member.setJoinedAt(LocalDateTime.now());

        pendingMember = new CommunityMember();
        pendingMember.setId(4L);
        pendingMember.setCommunityId(communityId);
        pendingMember.setUserId("pending-user");
        pendingMember.setRole(MemberRole.MEMBER);
        pendingMember.setStatus(MemberStatus.PENDING);
        pendingMember.setJoinedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Üyeleri sayfalı olarak listeleme")
    void getMembers_Success() {
        // Given
        Page<CommunityMember> page = new PageImpl<>(Arrays.asList(owner, admin, member));

        when(memberRepository.existsByCommunityIdAndUserId(communityId, ownerId)).thenReturn(true);
        when(memberRepository.findByCommunityIdAndStatus(eq(communityId), eq(MemberStatus.APPROVED),
                any(Pageable.class)))
                .thenReturn(page);

        // When
        PageResponse<MemberResponse> response = memberService.getMembers(communityId, ownerId, 0, 20);

        // Then
        assertNotNull(response);
        assertEquals(3, response.getContent().size());
        assertEquals(3, response.getTotalElements());
    }

    @Test
    @DisplayName("Üye olmayan kullanıcı üye listesini görememeli")
    void getMembers_NotMember_ThrowsException() {
        // Given
        when(memberRepository.existsByCommunityIdAndUserId(communityId, "stranger")).thenReturn(false);

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.getMembers(communityId, "stranger", 0, 20));

        assertEquals("Bu topluluğun üyesi değilsiniz", exception.getMessage());
    }

    @Test
    @DisplayName("Bekleyen üyeleri listeleme")
    void getPendingMembers_Success() {
        // Given
        Page<CommunityMember> page = new PageImpl<>(Arrays.asList(pendingMember));

        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findByCommunityIdAndStatus(eq(communityId), eq(MemberStatus.PENDING),
                any(Pageable.class)))
                .thenReturn(page);

        // When
        PageResponse<MemberResponse> response = memberService.getPendingMembers(communityId, ownerId, 0, 20);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(MemberStatus.PENDING, response.getContent().get(0).getStatus());
    }

    @Test
    @DisplayName("Üye rolü güncelleme")
    void updateMemberRole_Success() {
        // Given
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(MemberRole.ADMIN);

        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(CommunityMember.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        MemberResponse response = memberService.updateMemberRole(communityId, 3L, request, ownerId);

        // Then
        assertNotNull(response);
        assertEquals(MemberRole.ADMIN, response.getRole());
        verify(memberRepository).save(argThat(m -> m.getRole() == MemberRole.ADMIN));
    }

    @Test
    @DisplayName("Owner rolü değiştirilemez")
    void updateMemberRole_OwnerRole_ThrowsException() {
        // Given
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(MemberRole.ADMIN);

        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(owner));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.updateMemberRole(communityId, 1L, request, ownerId));

        assertEquals("Topluluk sahibinin rolü değiştirilemez", exception.getMessage());
    }

    @Test
    @DisplayName("OWNER rolü atanamaz")
    void updateMemberRole_AssignOwner_ThrowsException() {
        // Given
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(MemberRole.OWNER);

        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(member));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.updateMemberRole(communityId, 3L, request, ownerId));

        assertEquals("OWNER rolü atanamaz", exception.getMessage());
    }

    @Test
    @DisplayName("Üye onaylama")
    void approveMember_Success() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(4L)).thenReturn(Optional.of(pendingMember));
        when(memberRepository.save(any(CommunityMember.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        MemberResponse response = memberService.approveMember(communityId, 4L, ownerId);

        // Then
        assertEquals(MemberStatus.APPROVED, response.getStatus());
    }

    @Test
    @DisplayName("Zaten onaylanmış üye tekrar onaylanamaz")
    void approveMember_AlreadyApproved_ThrowsException() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(member));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.approveMember(communityId, 3L, ownerId));

        assertEquals("Üye zaten onaylanmış veya reddedilmiş", exception.getMessage());
    }

    @Test
    @DisplayName("Üye çıkarma")
    void removeMember_Success() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(3L)).thenReturn(Optional.of(member));

        // When
        memberService.removeMember(communityId, 3L, ownerId);

        // Then
        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("Owner çıkarılamaz")
    void removeMember_Owner_ThrowsException() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(owner));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.removeMember(communityId, 1L, ownerId));

        assertEquals("Topluluk sahibi çıkarılamaz", exception.getMessage());
    }

    @Test
    @DisplayName("Admin diğer admini çıkaramaz")
    void removeMember_AdminRemoveAdmin_ThrowsException() {
        // Given
        CommunityMember anotherAdmin = new CommunityMember();
        anotherAdmin.setId(5L);
        anotherAdmin.setCommunityId(communityId);
        anotherAdmin.setRole(MemberRole.ADMIN);

        when(memberRepository.findByCommunityIdAndUserId(communityId, adminId)).thenReturn(Optional.of(admin));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(anotherAdmin));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.removeMember(communityId, 5L, adminId));

        assertEquals("Diğer yöneticileri çıkarma yetkiniz yok", exception.getMessage());
    }

    @Test
    @DisplayName("Topluluktan ayrılma")
    void leaveCommunity_Success() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(communityId, memberId)).thenReturn(Optional.of(member));

        // When
        memberService.leaveCommunity(communityId, memberId);

        // Then
        verify(memberRepository).delete(member);
    }

    @Test
    @DisplayName("Owner topluluktan ayrılamaz")
    void leaveCommunity_Owner_ThrowsException() {
        // Given
        when(memberRepository.findByCommunityIdAndUserId(communityId, ownerId)).thenReturn(Optional.of(owner));

        // When & Then
        ApiException exception = assertThrows(ApiException.class,
                () -> memberService.leaveCommunity(communityId, ownerId));

        assertTrue(exception.getMessage().contains("Topluluk sahibi olarak ayrılamazsınız"));
    }
}
