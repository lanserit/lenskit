/*
 * LensKit, a reference implementation of recommender algorithms.
 * Copyright 2010-2011 Regents of the University of Minnesota
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.lenskit.eval.traintest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Provider;

import org.grouplens.lenskit.data.dao.DataAccessObject;
import org.grouplens.lenskit.data.snapshot.PackedRatingSnapshot;
import org.grouplens.lenskit.eval.AlgorithmInstance;
import org.grouplens.lenskit.eval.Job;
import org.grouplens.lenskit.eval.JobGroup;
import org.grouplens.lenskit.eval.PreparationContext;
import org.grouplens.lenskit.eval.PreparationException;
import org.grouplens.lenskit.eval.SharedRatingSnapshot;
import org.grouplens.lenskit.eval.data.traintest.TTDataSet;
import org.grouplens.lenskit.eval.predict.PredictionEvaluator;
import org.grouplens.lenskit.tablewriter.TableWriter;
import org.grouplens.lenskit.tablewriter.TableWriters;
import org.grouplens.lenskit.util.LazyValue;
import org.grouplens.lenskit.util.TaskTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Run train-test evaluations of several algorithms over a data set.
 * 
 * @since 0.8
 * @author Michael Ekstrand <ekstrand@cs.umn.edu>
 *
 */
public class TTPredictEvalJobGroup implements JobGroup {
    private static final Logger logger = LoggerFactory.getLogger(TTPredictEvalJobGroup.class);
    
    private TTDataSet dataSet;
    private List<Job> jobs;
    private Map<String,Integer> algoColIndexes;

    private TTPredictEvaluation evaluation;

    public TTPredictEvalJobGroup(TTPredictEvaluation eval,
                                 List<AlgorithmInstance> algos,
                                 List<PredictionEvaluator> evals,
                                 Map<String,Integer> acIndexes,
                                 TTDataSet data) {
        evaluation = eval;
        dataSet = data;
        algoColIndexes = acIndexes;
        
        final Provider<SharedRatingSnapshot> snap =
                new LazyValue<SharedRatingSnapshot>(new Callable<SharedRatingSnapshot>() {
                    @Override
                    public SharedRatingSnapshot call() {
                        logger.info("Loading snapshot for {}", getName());
                        TaskTimer timer = new TaskTimer();
                        SharedRatingSnapshot snap = loadSnapshot();
                        logger.info("Rating snapshot for {} loaded in {}",
                                    getName(), timer);
                        return snap;
                    }
                });
        
        jobs = new ArrayList<Job>(algos.size());
        for (AlgorithmInstance algo: algos) {
            jobs.add(new TTPredictEvalJob(algo, evals, data, snap,
                                          new OutputProvider(algo)));
        }
    }

    @Override
    public void prepare(PreparationContext context) throws PreparationException {
        context.prepare(dataSet);
    }

    @Override
    public String getName() {
        return dataSet.getName();
    }

    @Override
    public void start() {
        /* nothing to do */
    }

    @Override
    public void finish() {
        dataSet.release();
    }

    @Override
    public List<Job> getJobs() {
        return jobs;
    }
    
    private SharedRatingSnapshot loadSnapshot() {
        DataAccessObject dao = dataSet.getTrainFactory().create();
        try {
            return new SharedRatingSnapshot(new PackedRatingSnapshot.Builder(dao).build());
        } finally {
            dao.close();
        }
    }
    
    /**
     * Provide a table writer for the evaluation of a particular algorithm.
     * @author Michael Ekstrand <ekstrand@cs.umn.edu>
     *
     */
    class OutputProvider implements Provider<TableWriter> {
        private AlgorithmInstance algorithm;

        public OutputProvider(AlgorithmInstance algo) {
            algorithm = algo;
        }
        
        @Override
        public TableWriter get() {
            TableWriter output = evaluation.getOutputTable();
            String[] cols = new String[algoColIndexes.size() + 2];
            cols[0] = getName();
            cols[1] = algorithm.getName();
            for (Map.Entry<String, Object> entry: algorithm.getAttributes().entrySet()) {
                cols[algoColIndexes.get(entry.getKey())] = entry.getValue().toString();
            }
            return TableWriters.prefixed(output, cols);
        }
    }

    @Override
    public long lastUpdated(PreparationContext context) {
        return dataSet.lastUpdated(context);
    }

}