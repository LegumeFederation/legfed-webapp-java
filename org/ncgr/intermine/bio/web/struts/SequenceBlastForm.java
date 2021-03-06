package org.ncgr.intermine.bio.web.struts;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionMapping;
import org.intermine.web.struts.TableExportForm;

/**
 * Form for sequence export (FASTA etc.) to BLAST service.
 *
 * @author Kim Rutherford
 * @author Sam Hokin
 */
public class SequenceBlastForm extends TableExportForm {
    
    private static final long serialVersionUID = 1L;
    private String sequencePath;

    /**
     * Constructor
     */
    public SequenceBlastForm() {
        initialise();
    }

    /**
     * Initialiser
     */
    @Override
    public void initialise() {
        super.initialise();
        sequencePath = null;
    }

    /**
     * Sets the selected sequence path.  ie. the sequence to export
     *
     * @param sequencePath the selected path
     */
    public void setSequencePath(String sequencePath) {
        this.sequencePath = sequencePath;
    }

    /**
     * Gets the selected path
     *
     * @return the selected path
     */
    public String getSequencePath() {
        return sequencePath;
    }

    /**
     * Reset the form to the initial state
     *
     * @param mapping the mapping
     * @param request the request
     */
    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        initialise();
    }
}
