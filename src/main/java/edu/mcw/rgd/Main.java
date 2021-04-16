package edu.mcw.rgd;

import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mtutaj
 * @since Dec 12, 2017
 */
public class Main {

    Logger log = Logger.getLogger("status");

    private String version;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        Main loader = (Main) (bf.getBean("main"));

        Strain2AlleleAnnotator strainAnnotator = (Strain2AlleleAnnotator) (bf.getBean("strainAnnotator"));
        Allele2GeneAnnotator alleleAnnotator = (Allele2GeneAnnotator) (bf.getBean("alleleAnnotator"));

        try {
            loader.run(strainAnnotator, alleleAnnotator);
        } catch (Exception e) {
            Utils.printStackTrace(e, loader.log);
            throw e;
        }
    }
    
    void run(BaseAnnotator strainAnnotator, BaseAnnotator alleleAnnotator) throws Exception {

        Date dateStart = new Date();
        log.info(getVersion());

        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("  started at: "+sdt.format(dateStart));
        log.info("  "+strainAnnotator.getDao().getConnectionInfo());
        log.info("===");

        // create mutant-strain TO allele TO gene TO orthologous genes annotations
        strainAnnotator.run();

        // create mutant-allele TO gene TO orthologous genes annotations
        alleleAnnotator.run();

        log.info("=== DONE ===  elapsed: " + Utils.formatElapsedTime(dateStart.getTime(), System.currentTimeMillis()));
        log.info("");
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

