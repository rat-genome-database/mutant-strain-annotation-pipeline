package edu.mcw.rgd;

// common methods, used by both annotators

import edu.mcw.rgd.datamodel.SpeciesType;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseAnnotator {

    private Dao dao;

    // species to make orthologous gene disease annotations
    private Set<Integer> allowedSpeciesTypes = new HashSet<>();

    public BaseAnnotator() {
        allowedSpeciesTypes.add(SpeciesType.HUMAN);
        allowedSpeciesTypes.add(SpeciesType.MOUSE);
    }

    public abstract void run() throws Exception;

    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public Set<Integer> getAllowedSpeciesTypes() {
        return allowedSpeciesTypes;
    }
}
