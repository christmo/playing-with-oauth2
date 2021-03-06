/**
 * original author @author jdlee see http://blogs.steeplesoft.com/posts/2013/a-simple-oauth2-client-and-server-example-part-i.html
 */
package org.bjason.oauth2;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.oltu.oauth2.as.issuer.MD5Generator;
import org.apache.oltu.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.oltu.oauth2.as.request.OAuthAuthzRequest;
import org.apache.oltu.oauth2.as.response.OAuthASResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Path("/auth")
public class AuthResource {

	@Autowired
	private Database database;


	
	@GET
	public Response authorize(@Context HttpServletRequest request)
			throws URISyntaxException, OAuthSystemException {
		try {
			OAuthAuthzRequest oauthRequest = new OAuthAuthzRequest(request);
			
			if ( Common.checkClientIdandSecret(oauthRequest) == false ) {
				return Common.unauthorisedResponse();
			}
			
			OAuthIssuerImpl oauthIssuerImpl = new OAuthIssuerImpl(
					new MD5Generator());

			String responseType = oauthRequest
					.getParam(OAuth.OAUTH_RESPONSE_TYPE);

			OAuthASResponse.OAuthAuthorizationResponseBuilder builder = OAuthASResponse
					.authorizationResponse(request, HttpServletResponse.SC_OK);

			if (responseType.equals(ResponseType.CODE.toString())) {
				final String authorizationCode = oauthIssuerImpl
						.authorizationCode();
				GenerictokenData at = new GenerictokenData(authorizationCode,Common.SIXTY_SECONDS);

				Set<String> scopes = oauthRequest.getScopes();
				if (scopes != null) {
					at.addPemission(scopes);
				}
				database.addTokenCode(at);
				builder.setCode(authorizationCode);
			}
			if (responseType.equals(ResponseType.TOKEN.toString())) {
				final String accessToken = oauthIssuerImpl.accessToken();
				GenerictokenData at = new GenerictokenData(accessToken, Common.FIVE_MINUTES);
				database.addAccessToken(at);

				builder.setAccessToken(accessToken);
				builder.setExpiresIn(at.getExpires());
			}

			String redirectURI = oauthRequest
					.getParam(OAuth.OAUTH_REDIRECT_URI);
			final OAuthResponse response = builder.location(redirectURI)
					.buildQueryMessage();
			
			if ( redirectURI != null  ) {
				URI url = new URI(response.getLocationUri());
				return Response.status(Status.FOUND).location(url).build();
			}
			
			return Response.ok(builder.buildBodyMessage().getBody(),MediaType.APPLICATION_FORM_URLENCODED).build();
		} catch (OAuthProblemException e) {
			final Response.ResponseBuilder responseBuilder = Response
					.status(HttpServletResponse.SC_OK);
			String redirectUri = e.getRedirectUri();

			if (OAuthUtils.isEmpty(redirectUri)) {
				throw new WebApplicationException(responseBuilder.entity(
						"OAuth callback url needs to be provided by client!!!")
						.build());
			}
			final OAuthResponse response = OAuthASResponse
					.errorResponse(HttpServletResponse.SC_OK).error(e)
					.location(redirectUri).buildQueryMessage();
			final URI location = new URI(response.getLocationUri());
			return responseBuilder.location(location).build();
		}
	}
}
