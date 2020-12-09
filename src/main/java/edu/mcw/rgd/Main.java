package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.AssociationDAO;
import edu.mcw.rgd.datamodel.Strain;
import edu.mcw.rgd.datamodel.Strain2MarkerAssociation;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author mtutaj
 * @since Dec 12, 2017
 */
public class Main {

    Logger log = Logger.getLogger("status");

    private Dao dao;
    private int createdBy;
    private String version;
    private Set<String> strainRestrictedQualifiers;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        Main loader = (Main) (bf.getBean("main"));
        try {
            loader.run();
        } catch (Exception e) {
            Utils.printStackTrace(e, loader.log);
            throw e;
        }
    }
    
    void run() throws Exception {
        Date dateStart = new Date();
        log.info(getVersion());

        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("  started at: "+sdt.format(dateStart));
        log.info("  "+dao.getConnectionInfo());
        log.info("===");


        CounterPool counters = new CounterPool();

        // TODO:
        List<Annotation> baseAnnots = getDao().getBaseAnnotations("D");
        counters.add("  base annotations for disease ontology ", baseAnnots.size());

        AnnotCache alleleAnnots = new AnnotCache();
        baseAnnots.parallelStream().forEach( a -> {
            List<Strain2MarkerAssociation> geneAlleles;

            try {
                geneAlleles = getDao().getGeneAlleles(a.getAnnotatedObjectRgdId());
                if( geneAlleles.isEmpty() ) {
                    counters.increment("  base annotations without gene alleles");
                } else if( geneAlleles.size()==1 ) {
                    counters.increment("  base annotations with one gene allele");

                    Annotation alleleAnn = qcGeneAllele(a, geneAlleles.get(0));
                    if( alleleAnn.getKey()==0 ) {
                        counters.increment("   gene allele annotations to be inserted");
                        alleleAnnots.add(alleleAnn);
                    } else {
                        counters.increment("   gene allele annotations up-to-date");
                    }


                } else {
                    counters.increment("  base annotations with multiple gene alleles");
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

        // insert gene alleles
        counters.add("   gene allele annotations x inserted", alleleAnnots.size());

        log.info(counters.dumpAlphabetically());

        log.info("=== DONE ===  elapsed: " + Utils.formatElapsedTime(dateStart.getTime(), System.currentTimeMillis()));
        log.info("");
    }

    Annotation qcGeneAllele(Annotation strainAnn, Strain2MarkerAssociation assoc) throws Exception {

        int geneAlleleRgdId = assoc.getDetailRgdId();

        // create incoming gene allele annotation
        Annotation alleleAnn = (Annotation) strainAnn.clone();
        alleleAnn.setKey(0);
        alleleAnn.setAnnotatedObjectRgdId(geneAlleleRgdId);

        // RULE: if an annotation has a strain-restricted qualifier, QUALIFIER and WITH_INFO must not be propagated
        if( strainAnn.getQualifier()!=null &&
              ( getStrainRestrictedQualifiers().contains(strainAnn.getQualifier())
             || strainAnn.getQualifier().startsWith("MODEL") )
        ) {
            alleleAnn.setQualifier(null);
            alleleAnn.setWithInfo(null);
        }

        alleleAnn.setCreatedBy(getCreatedBy());
        alleleAnn.setCreatedDate(new Date());
        alleleAnn.setLastModifiedBy(getCreatedBy());
        alleleAnn.setLastModifiedDate(new Date());

        int annotKey = getDao().getAnnotationKey(alleleAnn);
        if( annotKey!=0 ) {
            alleleAnn.setKey(annotKey);
        }
        return alleleAnn;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public Dao getDao() {
        return dao;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setStrainRestrictedQualifiers(Set<String> strainRestrictedQualifiers) {
        this.strainRestrictedQualifiers = strainRestrictedQualifiers;
    }

    public Set<String> getStrainRestrictedQualifiers() {
        return strainRestrictedQualifiers;
    }
}

