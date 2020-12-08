package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.AssociationDAO;
import edu.mcw.rgd.dao.impl.OrthologDAO;
import edu.mcw.rgd.dao.impl.StrainDAO;
import edu.mcw.rgd.datamodel.Ortholog;
import edu.mcw.rgd.datamodel.Strain;
import edu.mcw.rgd.datamodel.Strain2MarkerAssociation;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mtutaj
 * @since Dec 8, 2020
 * <p>
 * wrapper to handle all DAO code
 */
public class Dao {

    AnnotationDAO annotationDAO = new AnnotationDAO();
    AssociationDAO associationDAO = new AssociationDAO();
    OrthologDAO orthologDAO = new OrthologDAO();
    StrainDAO strainDAO = new StrainDAO();

    Logger log = Logger.getLogger("status");
    Logger logInserted = Logger.getLogger("inserted");
    Logger logDeleted = Logger.getLogger("deleted");

    private Set<String> processedEvidenceCodes;

    public String getConnectionInfo() {
        return annotationDAO.getConnectionInfo();
    }

    public List<Strain> getMutantStrains() throws Exception {
        return strainDAO.getStrainsByType("mutant");
    }

    public List<Annotation> getBaseAnnotations(String aspect) throws Exception {

        List<Strain> mutantStrains = getMutantStrains();
        log.info("  mutant strains: "+mutantStrains.size());
        List<Integer> mutantStrainRgdIds = new ArrayList<>(mutantStrains.size());
        for( Strain s: mutantStrains ) {
            mutantStrainRgdIds.add(s.getRgdId());
        }

        List<Annotation> annots = new ArrayList<>();
        for( int i=0; i<mutantStrainRgdIds.size(); i+=1000 ) {
            int j = i+1000;
            if( j>mutantStrainRgdIds.size() ) {
                j = mutantStrainRgdIds.size();
            }
            List<Integer> rgdIds = mutantStrainRgdIds.subList(i, j);
            annots.addAll( annotationDAO.getAnnotationsByRgdIdsListAndAspect(rgdIds, aspect) );
        }
        log.info("  mutant strain annots with aspect "+aspect+": "+mutantStrains.size());

        // keep only annotations with approved evidence codes
        annots.removeIf(a -> !getProcessedEvidenceCodes().contains(a.getEvidence()));

        Collections.shuffle(annots);

        return annots;
    }

    public List<Strain2MarkerAssociation> getGeneAlleles(int rgdId) throws Exception {
        // retrieve all strain 2 gene associations
        // leave only alleles (marker_type='allele' or marker_type is NULL)
        List<Strain2MarkerAssociation> geneAlleles = associationDAO.getStrain2GeneAssociations(rgdId);
        geneAlleles.removeIf(i -> !Utils.stringsAreEqual(Utils.NVL(i.getMarkerType(), "allele"), "allele"));
        return geneAlleles;
    }


    synchronized public List<Ortholog> getOrthologsForSourceRgdId(int rgdId, Set<Integer> allowedSpeciesTypeKeys) throws Exception {
        List<Ortholog> orthos = _orthoCache.get(rgdId);
        if( orthos==null ) {
            orthos = orthologDAO.getOrthologsForSourceRgdId(rgdId);
            _orthoCache.put(rgdId, orthos);

            orthos.removeIf(o -> !allowedSpeciesTypeKeys.contains(o.getDestSpeciesTypeKey()));
        }
        return orthos;
    }
    Map<Integer, List<Ortholog>> _orthoCache = new HashMap<>();


    public int getAnnotationCount(int rgdId, String termAcc, String qualifier, int refRgdId) throws Exception {

        String key = rgdId+"|"+termAcc+"|"+qualifier;
        Integer cnt = _annotCache2.get(key);
        if( cnt!=null ) {
            return cnt;
        }

        List<Annotation> annots = annotationDAO.getAnnotations(rgdId, termAcc);
        Iterator<Annotation> it = annots.iterator();
        while( it.hasNext() ) {
            Annotation a = it.next();
            if( refRgdId==a.getRefRgdId() ) {
                it.remove();
                continue;
            }
            if( !Utils.stringsAreEqual(qualifier, a.getQualifier()) ) {
                it.remove();
            }
        }
        _annotCache2.put(key, annots.size());
        return annots.size();
    }
    static ConcurrentHashMap<String, Integer> _annotCache2 = new ConcurrentHashMap<>();

    /**
     * get annotation key by a list of values that comprise unique key:
     * TERM_ACC+ANNOTATED_OBJECT_RGD_ID+REF_RGD_ID+EVIDENCE+WITH_INFO+QUALIFIER+XREF_SOURCE
     * @param annot Annotation object with the following fields set: TERM_ACC+ANNOTATED_OBJECT_RGD_ID+REF_RGD_ID+EVIDENCE+WITH_INFO+QUALIFIER+XREF_SOURCE
     * @return value of annotation key or 0 if there is no such annotation
     * @throws Exception on spring framework dao failure
     */
    public int getAnnotationKey(Annotation annot) throws Exception {
        return annotationDAO.getAnnotationKey(annot);
    }

    public Annotation getAnnotation(int annotKey) throws Exception {
        return annotationDAO.getAnnotation(annotKey);
    }

    public void updateAnnotation(Annotation annot) throws Exception {
        annotationDAO.updateAnnotation(annot);
    }

    /**
     * Insert new annotation into FULL_ANNOT table; full_annot_key will be auto generated from sequence and returned
     * <p>
     * Note: this implementation uses only one roundtrip to database vs traditional approach resulting in double throughput
     * @param annot Annotation object representing column values
     * @throws Exception
     * @return value of new full annot key
     */
    public int insertAnnotation(Annotation annot) throws Exception{
        logInserted.debug(annot.dump("|"));
        return annotationDAO.insertAnnotation(annot);
    }

    public int updateLastModified(List<Integer> fullAnnotKeys) throws Exception{
        return annotationDAO.updateLastModified(fullAnnotKeys);
    }

    public int deleteAnnotationsCreatedBy(int createdBy, Date dt, int refRgdId, Logger log) throws Exception{

        List<Annotation> staleAnnots = annotationDAO.getAnnotationsModifiedBeforeTimestamp(createdBy, dt, refRgdId);
        log.debug("  stale annots found = "+staleAnnots.size());
        if( staleAnnots.isEmpty() ) {
            return 0;
        }

        List<Integer> keys = new ArrayList<>(staleAnnots.size());
        for( Annotation a: staleAnnots ) {
            logDeleted.debug(a.dump("|"));
            keys.add(a.getKey());
        }

        int rws = annotationDAO.deleteAnnotations(keys);
        log.debug("  stale annots deleted = "+rws);
        return rws;
    }

    public void setProcessedEvidenceCodes(Set<String> processedEvidenceCodes) {
        this.processedEvidenceCodes = processedEvidenceCodes;
    }

    public Set<String> getProcessedEvidenceCodes() {
        return processedEvidenceCodes;
    }
}
