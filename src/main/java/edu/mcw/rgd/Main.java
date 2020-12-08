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

/**
 * @author mtutaj
 * @since Dec 12, 2017
 */
public class Main {

    Logger log = Logger.getLogger("status");

    private Dao dao;
    private int createdBy;
    private String version;
    
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

        baseAnnots.parallelStream().forEach( a -> {
            List<Strain2MarkerAssociation> geneAlleles;

            try {
                geneAlleles = getDao().getGeneAlleles(a.getAnnotatedObjectRgdId());
                if( geneAlleles.isEmpty() ) {
                    counters.increment("  base annotations without gene alleles");
                } else if( geneAlleles.size()==1 ) {
                    counters.increment("  base annotations with one gene allele");
                } else {
                    counters.increment("  base annotations with multiple gene alleles");
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

        log.info(counters.dumpAlphabetically());

        log.info("=== DONE ===  elapsed: " + Utils.formatElapsedTime(dateStart.getTime(), System.currentTimeMillis()));
        log.info("");
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
}

