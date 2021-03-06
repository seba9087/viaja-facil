package eu.hellek.gba.server.rpc.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.protobuf.CodedInputStream;

import eu.hellek.gba.model.Point;
import eu.hellek.gba.proto.Helpers;
import eu.hellek.gba.proto.RequestsProtos.IndirectSearchRequest;
import eu.hellek.gba.proto.SearchResultProtos.SearchResultProxy;
import eu.hellek.gba.proto.SearchResultProtos.SearchResultProxy.Builder;
import eu.hellek.gba.server.dao.Dao;
import eu.hellek.gba.server.rpc.ExtractedFunctions;
import eu.hellek.gba.server.rpc.ListPointsServiceImpl;
import eu.hellek.gba.server.utils.Utils;

public class IndirectSearchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final String location = "IndirectSearchServlet";
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		service(req, resp);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		service(req, resp);
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		InputStream is = req.getInputStream();
		try {
			IndirectSearchRequest r = IndirectSearchRequest.parseFrom(CodedInputStream.newInstance(is));
//			System.err.println("called: " + r);
			float lat1 = r.getLat1();
			float lon1 = r.getLon1();
			float lat2 = r.getLat2();
			float lon2 = r.getLon2();
			List<String> mlkSet1String = r.getMlkSet1StringList();
			List<String> mlkSet2String = r.getMlkSet2StringList();
			double distanceBetweenPoints = Utils.distanceInMeters(new Point("", lat1, lon1, null), new Point("", lat2, lon2, null));
			SearchResultProxy toSend;
			if(distanceBetweenPoints > 1000) {
				int requests = 0;
				if(req.getSession() != null && req.getSession().getId() != null) {
					requests = Dao.getInstance().addAndCheckSearchForIp(req.getSession().getId(), 1);
				} else {
					requests = Dao.getInstance().addAndCheckSearchForIp(req.getRemoteAddr(), 1);
				}
				if(requests < ListPointsServiceImpl.maxRequestsIndirect) {
					eu.hellek.gba.shared.SearchResultProxy res = new ExtractedFunctions().getIndirectConnections(lat1, lon1, lat2, lon2, r.getIgnoreTrains(), r.getIgnoreSubte(), mlkSet1String, mlkSet2String);
					toSend = Helpers.copyToProto(res);
				} else {
					Logger.getLogger(location).log(Level.WARNING, location + ": too many requests for ip " + req.getRemoteAddr() + ": " + requests);
					Builder builder = SearchResultProxy.newBuilder();
					builder.setError(eu.hellek.gba.shared.SearchResultProxy.tooManyReqests);
					toSend = builder.build();
				}
			} else {
				Logger.getLogger(location).log(Level.INFO, location + ": distance too little " + distanceBetweenPoints);
				Builder builder = SearchResultProxy.newBuilder();
				builder.setError(eu.hellek.gba.shared.SearchResultProxy.distanceTooLittle);
				toSend = builder.build();
			}
			toSend.writeTo(resp.getOutputStream());
		} catch (Exception e) {
			Logger.getLogger("IndirectSearchServlet").log(Level.SEVERE, "Error: " + e);
			e.printStackTrace();
		}
	}
	
}
