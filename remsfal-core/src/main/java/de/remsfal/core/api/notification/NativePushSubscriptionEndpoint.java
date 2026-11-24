package de.remsfal.core.api.notification;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import de.remsfal.core.json.notification.NativePushSubscriptionIdJson;
import de.remsfal.core.json.notification.NativePushSubscriptionJson;

@Path("/notification/v1/push-subscriptions")
public interface NativePushSubscriptionEndpoint {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register or refresh a native push subscription")
    @APIResponse(responseCode = "200", description = "Native push subscription saved")
    @APIResponse(responseCode = "400", description = "Invalid token or transport")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "409", description = "Token is already registered to another user")
    @APIResponse(responseCode = "503", description = "The selected push transport is unavailable")
    NativePushSubscriptionIdJson registerPushSubscription(@NotNull @Valid NativePushSubscriptionJson subscription);

    @POST
    @Path("/{id}/test")
    @Operation(summary = "Send a test push to the selected native subscription")
    @APIResponse(responseCode = "204", description = "Test push accepted by the provider")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    @APIResponse(responseCode = "404", description = "Subscription not found")
    @APIResponse(responseCode = "410", description = "Subscription expired")
    @APIResponse(responseCode = "502", description = "Push delivery failed")
    @APIResponse(responseCode = "503", description = "The selected push transport is unavailable")
    void sendTestPush(@PathParam("id") UUID id);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Remove a native push subscription")
    @APIResponse(responseCode = "204", description = "Subscription removed or absent")
    @APIResponse(responseCode = "401", description = "Not authenticated")
    void unregisterPushSubscription(@PathParam("id") UUID id);
}
