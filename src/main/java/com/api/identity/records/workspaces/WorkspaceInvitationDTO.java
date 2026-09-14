package com.api.identity.records.workspaces;

import com.api.identity.enums.InvitationStatus;
import com.api.identity.enums.WorkspaceRole;

import java.io.Serializable;
import java.time.LocalDateTime;

public record WorkspaceInvitationDTO(
        Long id,
        Long workspaceId,
        String workspaceName,
        String invitedByEmail,
        InvitationStatus status,
        WorkspaceRole role,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        // Derivado de expiresAt, no un status propio en la DB — una invitación PENDING vencida
        // sigue siendo PENDING hasta que alguien actúe (aceptar/rechazar la rechaza igual con
        // BusinessException); esto es lo que le permite al frontend mostrarla como "vencida" en
        // vez de con los botones de aceptar/rechazar normales.
        boolean expired) implements Serializable {
}
