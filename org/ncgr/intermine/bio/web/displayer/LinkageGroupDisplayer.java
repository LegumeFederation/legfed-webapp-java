package org.ncgr.intermine.bio.web.displayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;

import org.intermine.api.InterMineAPI;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.api.results.ExportResultsIterator;
import org.intermine.api.results.ResultElement;

import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;

import org.intermine.objectstore.ObjectStoreException;

import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.OrderDirection;
import org.intermine.pathquery.PathQuery;

import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

import org.apache.log4j.Logger;

import org.json.JSONObject;

/**
 * Display a diagram with linkage groups, markers and QTLs.
 * This displayer detects the type of report page (GeneticMap, LinkageGroup, QTL) and operates accordingly.
 * The corresponding JSP is linkageGroupDisplayer.jsp.
 *
 * @author Sam Hokin
 */
public class LinkageGroupDisplayer extends ReportDisplayer {

    protected static final Logger LOG = Logger.getLogger(LinkageGroupDisplayer.class);

    /**
     * Construct with config and the InterMineAPI.
     *
     * @param config to describe the report displayer
     * @param im the InterMine API
     */
    public LinkageGroupDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
        super(config, im);
    }

    @Override
    public void display(HttpServletRequest request, ReportObject reportObject) {

        // global since we use it in methods below
        PathQueryExecutor executor  = im.getPathQueryExecutor();
        
        int reportId = reportObject.getId();
        String objectName = reportObject.getClassDescriptor().getSimpleName();

        // get the linkage group(s) and find the maximum length
        Map<Integer,List<ResultElement>> lgMap = new LinkedHashMap<Integer,List<ResultElement>>();
        double maxLGLength = 0.0;
        PathQuery lgQuery = getLinkageGroupQuery(im.getModel(), reportId, objectName);
        ExportResultsIterator lgResult = getResults(executor, lgQuery);
        while (lgResult.hasNext()) {
            List<ResultElement> row = lgResult.next();
            Integer lgId = (Integer) row.get(0).getField();
            String lgIdentifier = (String) row.get(1).getField();
            double length = (double) (Double) row.get(2).getField();
            int number = (int) (Integer) row.get(3).getField();
            lgMap.put(lgId, row);
            if (length>maxLGLength) maxLGLength = length;
        }

        // bail if we got nothing
        if (lgMap.size()==0) {
            System.out.println("Exiting LinkageGroupDisplayer: no linkage group returned for reportId="+reportId+" and objectName="+objectName);
            return;
        }

        // get the genetic markers per linkage group
        Map<Integer,Map<Integer,List<ResultElement>>> markerMap = new LinkedHashMap<Integer,Map<Integer,List<ResultElement>>>();
        for (Integer lgId : lgMap.keySet()) {
            PathQuery markerQuery = getGeneticMarkerQuery(im.getModel(), (int)lgId);
            ExportResultsIterator markerResult = getResults(executor, markerQuery);
            Map<Integer,List<ResultElement>> markers = new LinkedHashMap<Integer,List<ResultElement>>();
            while (markerResult.hasNext()) {
                List<ResultElement> row = markerResult.next();
                Integer markerId = (Integer) row.get(0).getField();
                markers.put(markerId, row);
            }
            markerMap.put(lgId, markers);
        }

        // get the QTL ids per linkage group
        Map<Integer, Map<Integer,List<ResultElement>>> qtlMap = new LinkedHashMap<Integer,Map<Integer,List<ResultElement>>>();
        for (Integer lgId : lgMap.keySet()) {
            PathQuery qtlQuery = getQTLQuery(im.getModel(), (int)lgId);
            ExportResultsIterator qtlResult = getResults(executor, qtlQuery);
            Map<Integer,List<ResultElement>> qtls = new LinkedHashMap<Integer,List<ResultElement>>();
            while (qtlResult.hasNext()) {
                List<ResultElement> row = qtlResult.next();
                Integer qtlId = (Integer) row.get(0).getField();
                qtls.put(qtlId, row);
            }
            qtlMap.put(lgId, qtls);
        }

        // JSON data - non-labeled array - order matters!
        List<Object> trackData = new LinkedList<Object>();
        for (Integer lgId : lgMap.keySet()) {
            
            // LINKAGE GROUP TRACK
            // the data
            List<ResultElement> lgRow = lgMap.get(lgId);
            int lgRowId = (int) (Integer) lgRow.get(0).getField();
            String lgIdentifier = (String) lgRow.get(1).getField();
            double[] length = new double[2];
            length[0] = 0.0;
            length[1] = (double) (Double) lgRow.get(2).getField();
            // the track
            Map<String,Object> lgTrack = new LinkedHashMap<String,Object>();
            lgTrack.put("type", "box");
            // linkage group track data array
            List<Object> lgDataArray = new LinkedList<Object>();
            // the single data item
            Map<String,Object> lgData = new LinkedHashMap<String,Object>();
            lgData.put("id", lgIdentifier);
            lgData.put("key", lgId); // for linking
            lgData.put("fill", "purple");
            lgData.put("outline", "black");
            // linkage group box positions = array of one pair
            List<Object> lgPositionsArray = new LinkedList<Object>();
            lgPositionsArray.add(length);
            lgData.put("data", lgPositionsArray);
            lgDataArray.add(lgData);
            lgTrack.put("data", lgDataArray);
            trackData.add(lgTrack);

            // MARKERS TRACK
            Map<String,Object> markersTrack = new LinkedHashMap<String,Object>();
            markersTrack.put("type", "triangle");
            // markers track data array
            List<Object> markersDataArray = new LinkedList<Object>();
            Map<Integer,List<ResultElement>> markers = markerMap.get(lgId);
            for (Integer markerId : markers.keySet()) {
                // the data
                List<ResultElement> markerRow = markers.get(markerId);
                int markerRowId = (int) (Integer) markerRow.get(0).getField();
                String markerIdentifier = (String) markerRow.get(1).getField();
                double position = (double) (Double) markerRow.get(2).getField();
                // the track data
                Map<String,Object> markerData = new LinkedHashMap<String,Object>();
                markerData.put("id", markerIdentifier);
                markerData.put("key", markerId); // for linking
                markerData.put("fill", "darkred");
                markerData.put("outline", "black");
                markerData.put("offset", position);
                markersDataArray.add(markerData);
            }
            markersTrack.put("data", markersDataArray);
            trackData.add(markersTrack);
            
            // QTLS TRACK
            Map<String,Object> qtlsTrack = new LinkedHashMap<String,Object>();
            qtlsTrack.put("type", "box");
            // QTLs track data array
            List<Object> qtlsDataArray = new LinkedList<Object>();
            Map<Integer,List<ResultElement>> qtls = qtlMap.get(lgId);
            for (Integer qtlId : qtls.keySet()) {
                // the data
                List<ResultElement> qtlRow = qtls.get(qtlId);
                int qtlRowId = (int) (Integer) qtlRow.get(0).getField();
                String qtlIdentifier = (String) qtlRow.get(1).getField();
                double[] span = new double[2];
                span[0] = (double) (Double) qtlRow.get(2).getField(); // QTL.start
                span[1] = (double) (Double) qtlRow.get(3).getField(); // QTL.end
                // the track data
                Map<String,Object> qtlData = new LinkedHashMap<String,Object>();
                qtlData.put("id", qtlIdentifier); // canvasXpress needs it to be called "id"
                qtlData.put("key", qtlId); // for linking
                qtlData.put("fill", "yellow");
                qtlData.put("outline", "black");
                // QTL box positions = array of one pair
                List<Object> qtlPositionsArray = new LinkedList<Object>();
                qtlPositionsArray.add(span);
                qtlData.put("data", qtlPositionsArray);
                qtlsDataArray.add(qtlData);
            }
            qtlsTrack.put("data", qtlsDataArray);
            trackData.add(qtlsTrack);
        }

        // output scalar vars
        request.setAttribute("tracksCount", lgMap.size());
        request.setAttribute("maxLGLength", maxLGLength);

        // entire thing is in a single tracks JSON array
        Map<String,Object> tracks = new LinkedHashMap<String,Object>();
        tracks.put("tracks", trackData);
        request.setAttribute("tracksJSON", new JSONObject(tracks).toString());

    }

    /**
     * Create a path query to retrieve linkage groups associated with a given genetic map, QTL or even a single linkage group Id.
     *
     * @param model the model
     * @param gmPI  the genetic map IM id
     * @param objectName the name of the report object, i.e. GeneticMap, QTL, LinkageGroup
     * @return the path query
     */
    PathQuery getLinkageGroupQuery(Model model, int reportId, String objectName) {
        PathQuery query = new PathQuery(model);
        query.addViews("LinkageGroup.id",
                       "LinkageGroup.identifier",
                       "LinkageGroup.length",
                       "LinkageGroup.number"
                       );
        if (objectName.equals("GeneticMap")) {
            query.addConstraint(Constraints.eq("LinkageGroup.geneticMap.id", String.valueOf(reportId)));
        } else if (objectName.equals("QTL")) {
            query.addConstraint(Constraints.eq("LinkageGroup.qtls.id", String.valueOf(reportId)));
        } else if (objectName.equals("LinkageGroup")) {
            query.addConstraint(Constraints.eq("LinkageGroup.id", String.valueOf(reportId)));
        }
        query.addOrderBy("LinkageGroup.number", OrderDirection.ASC);
        return query;
    }


    /**
     * Create a path query to retrieve genetic markers associated with a given linkage group.
     *
     * @param model the model
     * @param lgId  the linkage group id
     * @return the path query
     */
    PathQuery getGeneticMarkerQuery(Model model, int lgId) {
        PathQuery query = new PathQuery(model);
        query.addViews("GeneticMarker.id",
                       "GeneticMarker.secondaryIdentifier",
                       "GeneticMarker.linkageGroupPositions.position"
                       );
        query.addConstraint(Constraints.eq("GeneticMarker.linkageGroupPositions.linkageGroup.id", String.valueOf(lgId)));
        query.addOrderBy("GeneticMarker.linkageGroupPositions.position", OrderDirection.ASC);
        return query;
    }

    /**
     * Create a path query to retrieve QTLs associated with a given linkage group.
     *
     * @param model the model
     * @param lgId  the linkage group id
     * @return the path query
     */
    PathQuery getQTLQuery(Model model, int lgId) {
        PathQuery query = new PathQuery(model);
        query.addViews("QTL.id",
                       "QTL.identifier",
                       "QTL.start",
                       "QTL.end"
                       );
        query.addConstraint(Constraints.eq("QTL.linkageGroup.id", String.valueOf(lgId)));
        query.addOrderBy("QTL.start", OrderDirection.ASC);
        return query;
    }

    /**
     * Execute a PathQuery. Just a wrapper to throw an exception so we don't have to do a try/catch block every time above.
     *
     * @param executor the PathQueryExecutor
     * @param query    the PathQuery
     * @return the ExportResultsIterator
     */
    ExportResultsIterator getResults(PathQueryExecutor executor, PathQuery query) {
        try {
            return executor.execute(query);
        } catch (ObjectStoreException ex) {
            throw new RuntimeException("Error retrieving data.", ex);
        }
    }

}
