package com.google.gridworks.operations.recon;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.gridworks.commands.freebase.UploadDataCommand;
import com.google.gridworks.history.HistoryEntry;
import com.google.gridworks.model.AbstractOperation;
import com.google.gridworks.model.Cell;
import com.google.gridworks.model.Project;
import com.google.gridworks.model.Recon;
import com.google.gridworks.model.Row;
import com.google.gridworks.model.changes.MassReconChange;
import com.google.gridworks.operations.OperationRegistry;
import com.google.gridworks.util.ParsingUtilities;

public class ImportQADataOperation extends AbstractOperation {
    static public AbstractOperation reconstruct(Project project, JSONObject obj) throws Exception {
        return new ImportQADataOperation();
    }
    
    public ImportQADataOperation() {
    }

    public void write(JSONWriter writer, Properties options)
            throws JSONException {
        
        writer.object();
        writer.key("op"); writer.value(OperationRegistry.s_opClassToName.get(this.getClass()));
        writer.key("description"); writer.value(getBriefDescription(null));
        writer.endObject();
    }
    
    @Override
    protected String getBriefDescription(Project project) {
        return "Import QA DAta";
    }
    
    @Override
    protected HistoryEntry createHistoryEntry(Project project, long historyEntryID) throws Exception {
        Integer jobID = (Integer) project.getMetadata().getPreferenceStore().get(UploadDataCommand.s_dataLoadJobIDPref);
        if (jobID == null) {
            throw new InternalError("Project is not associated with any data loading job.");
        }

        Map<Long, String> reconIDToResult = new HashMap<Long, String>();
        
        URL url = new URL("http://gridworks-loads.dfhuynh.user.dev.freebaseapps.com/get_answers/" + jobID);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(30000); // 30 seconds
        
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(conn.getInputStream()));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject obj = ParsingUtilities.evaluateJsonStringToObject(line);
                long reconID = Long.parseLong(obj.getString("recon_id").substring(3));
                
                reconIDToResult.put(reconID, obj.getString("result"));
            }
        } finally {
            reader.close();
        }
        
        Map<Long, Recon> oldRecons = new HashMap<Long, Recon>();
        Map<Long, Recon> newRecons = new HashMap<Long, Recon>();
        
        for (int r = 0; r < project.rows.size(); r++) {
            Row row = project.rows.get(r);
            
            for (int c = 0; c < row.cells.size(); c++) {
                Cell cell = row.cells.get(c);
                if (cell != null && cell.recon != null) {
                    Recon oldRecon = cell.recon;
                    
                    if (reconIDToResult.containsKey(oldRecon.id)) {
                        Recon newRecon = oldRecon.dup();
                        newRecon.setFeature(Recon.Feature_qaResult, reconIDToResult.get(oldRecon.id));
                        
                        reconIDToResult.remove(oldRecon.id);
                        
                        oldRecons.put(oldRecon.id, oldRecon);
                        newRecons.put(oldRecon.id, newRecon);
                    }
                }
            }
        }

        return new HistoryEntry(
            historyEntryID, 
            project, 
            getBriefDescription(project), 
            this,
            new MassReconChange(newRecons, oldRecons)
        );
    }
}
