package de.remsfal.core.api.notification;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import de.remsfal.core.json.notification.WebPushPublicKeyJson;
import de.remsfal.core.json.notification.WebPushSubscriptionIdJson;
import de.remsfal.core.json.notification.WebPushSubscriptionJson;

@Path("/notification/v1/web-push")
public interface WebPushEndpoint {

    @GET
    @Path("/public-key")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get the public Web Push application server key.")
    @APIResponse(responseCode = "200", description = "Public VAPID key when Web Push is configured")
    @APIResponse(responseCode = "401", description = "No user authentication provided via session cookie")
    WebPushPublicKeyJson getWebPushPublicKey();

    @POST
    @Path("/subscriptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register or refresh a Web Push subscription for the authenticated user.")
    @APIResponse(responseCode = "200", description = "Subscription saved")
    @APIResponse(responseCode = "400", description = "Invalid push subscription")
    @APIResponse(responseCode = "401", description = "No user authentication provided via session cookie")
    @APIResponse(responseCode = "409", description = "Subscription already belongs to another account")
    @APIResponse(responseCode = "503", description = "Web Push is unavailable")
    WebPushSubscriptionIdJson saveWebPushSubscription(
        @RequestBody(required = true) @Valid @NotNull WebPushSubscriptionJson subscription);

    @DELETE
    @Path("/subscriptions/{id}")
    @Operation(summary = "Remove a Web Push subscription owned by the authenticated user.")
    @APIResponse(responseCode = "204", description = "Subscription removed or absent")
    @APIResponse(responseCode = "401", description = "No user authentication provided via session cookie")
    void deleteWebPushSubscription(@PathParam("id") UUID id);

    @POST
    @Path("/subscriptions/{id}/test")
    @Operation(summary = "Send a test Web Push to the authenticated user's specified browser.")
    @APIResponse(responseCode = "204", description = "Push accepted by the browser's push service")
    @APIResponse(responseCode = "401", description = "No user authentication provided via session cookie")
    @APIResponse(responseCode = "404", description = "Subscription not found")
    @APIResponse(responseCode = "410", description = "Subscription expired")
    @APIResponse(responseCode = "502", description = "Push delivery failed")
    @APIResponse(responseCode = "503", description = "Web Push is unavailable")
    void sendWebPushTest(@PathParam("id") UUID id);

}
