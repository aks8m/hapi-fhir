package ca.uhn.fhir.rest.server.interceptor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.RestfulServerUtils;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * This interceptor allows a client to request that a Media resource be
 * served as the raw contents of the resource, assuming either:
 * <ul>
 * <li>The client explicitly requests the correct content type using the Accept header</li>
 * <li>The client explicitly requests raw output by adding the parameter <code>_output=data</code></li>
 * </ul>
 */
public class ServeMediaResourceRawInterceptor extends InterceptorAdapter {

	public static final String MEDIA_CONTENT_CONTENT_TYPE_OPT = "Media.content.contentType";

	@Override
	public boolean outgoingResponse(RequestDetails theRequestDetails, IBaseResource theResponseObject, HttpServletRequest theServletRequest, HttpServletResponse theServletResponse) throws AuthenticationException {
		if (theResponseObject == null) {
			return true;
		}


		FhirContext context = theRequestDetails.getFhirContext();
		String resourceName = context.getResourceDefinition(theResponseObject).getName();

		// Are we serving a FHIR read request on the Media resource type
		if (!"Media".equals(resourceName) || theRequestDetails.getRestOperationType() != RestOperationTypeEnum.READ) {
			return true;
		}

		// What is the content type of the Media resource we're returning?
		String contentType = null;
		Optional<IPrimitiveType> contentTypeOpt = context.newFluentPath().evaluateFirst(theResponseObject, MEDIA_CONTENT_CONTENT_TYPE_OPT, IPrimitiveType.class);
		if (contentTypeOpt.isPresent()) {
			contentType = contentTypeOpt.get().getValueAsString();
		}

		// What is the data of the Media resource we're returning?
		IPrimitiveType<byte[]> data = null;
		Optional<IPrimitiveType> dataOpt = context.newFluentPath().evaluateFirst(theResponseObject, "Media.content.data", IPrimitiveType.class);
		if (dataOpt.isPresent()) {
			data = dataOpt.get();
		}

		if (isBlank(contentType) || data == null) {
			return true;
		}

		RestfulServerUtils.ResponseEncoding responseEncoding = RestfulServerUtils.determineResponseEncodingNoDefault(theRequestDetails, null, contentType);
		if (responseEncoding != null) {
			if (contentType.equals(responseEncoding.getContentType())) {
				returnRawResponse(theRequestDetails, theServletResponse, contentType, data);
				return false;

			}
		}

		String[] outputParam = theRequestDetails.getParameters().get("_output");
		if (outputParam != null && "data".equals(outputParam[0])) {
			returnRawResponse(theRequestDetails, theServletResponse, contentType, data);
			return false;
		}

		return true;
	}

	private void returnRawResponse(RequestDetails theRequestDetails, HttpServletResponse theServletResponse, String theContentType, IPrimitiveType<byte[]> theData) {
		theServletResponse.setStatus(200);
		if (theRequestDetails.getServer() instanceof RestfulServer) {
			RestfulServer rs = (RestfulServer) theRequestDetails.getServer();
			rs.addHeadersToResponse(theServletResponse);
		}

		theServletResponse.addHeader(Constants.HEADER_CONTENT_TYPE, theContentType);

		// Write the response
		try {
			theServletResponse.getOutputStream().write(theData.getValue());
			theServletResponse.getOutputStream().close();
		} catch (IOException e) {
			throw new InternalErrorException(e);
		}
	}
}