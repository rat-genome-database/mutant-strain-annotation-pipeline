package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.Map;

/**
 * @author mtutaj
 * @since Dec 8, 2020
 * <p>
 * wrapper to handle all DAO code
 */
public class Dao {

    AnnotationDAO annotationDAO = new AnnotationDAO();
    AssociationDAO associationDAO = new AssociationDAO();
    GeneDAO geneDAO = new GeneDAO();
    OrthologDAO orthologDAO = new OrthologDAO();
    StrainDAO strainDAO = new StrainDAO();

    Logger log = Logger.getLogger("status");
    Logger logInserted = Logger.getLogger("inserted");
    Logger logUpdated = Logger.getLogger("updated");
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
        log.info("  mutant strain annots with aspect "+aspect+": "+annots.size());

        // keep only annotations with approved evidence codes
        annots.removeIf(a -> !getProcessedEvidenceCodes().contains(a.getEvidence()));

        Collections.shuffle(annots);

        return annots;
    }

    public List<Strain2MarkerAssociation> getGeneAllelesForStrain(int rgdId) throws Exception {
        // retrieve all strain 2 gene associations
        // leave only alleles (marker_type='allele' or marker_type is NULL)
        List<Strain2MarkerAssociation> geneAlleles = associationDAO.getStrain2GeneAssociations(rgdId);
        geneAlleles.removeIf(i -> !Utils.stringsAreEqual(Utils.NVL(i.getMarkerType(), "allele"), "allele"));
        return geneAlleles;
    }

    public Gene getGene(int rgdId) throws Exception {
        return geneDAO.getGene(rgdId);
    }

    public List<Gene> getGeneFromAllele(int alleleRgdId) throws Exception {
        return geneDAO.getGeneFromVariant(alleleRgdId);
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


    public int getAnnotationKey(Annotation annot) throws Exception {
        return annotationDAO.getAnnotationKey(annot);
    }

    public List<Annotation> getAnnotationsModifiedBeforeTimestamp(int createdBy, Date dt, String aspect) throws Exception{
        return annotationDAO.getAnnotationsModifiedBeforeTimestamp(createdBy, dt, aspect);
    }

    public void updateAnnotation(Annotation a, Annotation annotInRgd) throws Exception {

        String msg = "KEY:" + a.getKey() + " " + a.getTermAcc() + " RGD:" + a.getAnnotatedObjectRgdId() + " RefRGD:" + a.getRefRgdId() + " " + a.getEvidence() + " W:" + a.getWithInfo();
        if( !Utils.stringsAreEqual(annotInRgd.getAnnotationExtension(), a.getAnnotationExtension()) ) {
            msg += "\n   ANNOT_EXT  OLD["+Utils.NVL(annotInRgd.getAnnotationExtension(),"")+"]  NEW["+a.getAnnotationExtension()+"]";
        }
        if( !Utils.stringsAreEqual(annotInRgd.getGeneProductFormId(), a.getGeneProductFormId()) ) {
            msg += "\n   GENE_FORM  OLD["+Utils.NVL(annotInRgd.getGeneProductFormId(),"")+"]  NEW["+a.getGeneProductFormId()+"]";
        }
        if( !Utils.stringsAreEqual(annotInRgd.getNotes(), a.getNotes()) ) {
            msg += "\n   NOTES  OLD["+Utils.NVL(annotInRgd.getNotes(),"")+"]  NEW["+a.getNotes()+"]";
        }
        logUpdated.info(msg);
        annotationDAO.updateAnnotation(a);
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

    public void updateLastModified(int fullAnnotKey) throws Exception{
        annotationDAO.updateLastModified(fullAnnotKey);
    }

    public void deleteAnnotation(Annotation a) throws Exception{
        logDeleted.debug(a.dump("|"));
        annotationDAO.deleteAnnotation(a.getKey());
    }

    public void setProcessedEvidenceCodes(Set<String> processedEvidenceCodes) {
        this.processedEvidenceCodes = processedEvidenceCodes;
    }

    public Set<String> getProcessedEvidenceCodes() {
        return processedEvidenceCodes;
    }
}
