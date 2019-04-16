package com.adobe.qe.toughday.internal.core.k8s.redistribution.runmodes;

import com.adobe.qe.toughday.internal.core.engine.runmodes.ConstantLoad;
import com.adobe.qe.toughday.internal.core.k8s.redistribution.RebalanceInstructions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;


public class ConstantLoadRunModeBalancer extends AbstractRunModeBalancer<ConstantLoad> {
    protected static final Logger LOG = LogManager.getLogger(ConstantLoadRunModeBalancer.class);


    @Override
    public Map<String, String> getRunModePropertiesToRedistribute(Class type, ConstantLoad runMode) {
        Map<String, String> runModeProps = super.getRunModePropertiesToRedistribute(type, runMode);

        if (runMode.isVariableLoad()) {
            // add current load
            runModeProps.put("currentload", String.valueOf(runMode.getCurrentLoad()));
        }

        return runModeProps;
    }

    @Override
    public void processRunModeInstructions(RebalanceInstructions rebalanceInstructions, ConstantLoad runMode) {
        super.processRunModeInstructions(rebalanceInstructions, runMode);

        Map<String, String> runModeProps = rebalanceInstructions.getRunModeProperties();
        if (runModeProps.containsKey("currentload")) {
            System.out.println("Changing current load from " + runMode.getCurrentLoad() + " to " +
                    runModeProps.get("currentload"));
            runMode.setCurrentLoad(Integer.parseInt(runModeProps.get("currentload")));
        }
    }

    private void processPropertyChange(String property, String newValue, ConstantLoad runMode) {
        if (property.equals("load") && !runMode.isVariableLoad()) {
            System.out.println("[constant load run mode balancer] Processing load change");

            long newLoad = Long.parseLong(newValue);
            long difference = runMode.getLoad() - newLoad;

            if  (difference > 0 ) {
                // remove part of the local run maps used by the workers
                runMode.removeRunMaps(difference);
                System.out.println("[constant load run mode balancer] Successfully deleted " + difference +
                        " run maps.");

                // TODO: Should we remove tests from cache before changing the count property?
            } else {
                // add some run maps to match the new load
                runMode.addRunMaps(Math.abs(difference));
                System.out.println("[constant load run mode balancer] Successfully deleted " + difference +
                        " run maps.");
            }
        }
    }

    @Override
    public void before(RebalanceInstructions rebalanceInstructions, ConstantLoad runMode) {
        System.out.println("[constant load run mode balancer] - before....");

        if (runMode.isVariableLoad()) {
            /* We must cancel the scheduled task and reschedule it with the new values for 'period' and
             * initial delay.
             */
            boolean cancelled = runMode.cancelPeriodicTask();
            if (!cancelled) {
                System.out.println("[constant load run mode balancer]  task could not be cancelled.");
                return;
            }

            System.out.println("[constant load run mode balancer] successfully cancelled task.");
        }

        Map<String, String> runModeProperties = rebalanceInstructions.getRunModeProperties();

        runModeProperties.forEach((property, propValue) ->
                processPropertyChange(property, propValue, runMode));
    }

    @Override
    public void after(RebalanceInstructions rebalanceInstructions, ConstantLoad runMode) {
        // reschedule the task
        if (runMode.isVariableLoad()) {
            runMode.schedulePeriodicTask();

            System.out.println("[constant load run mode balancer] successfully rescheduled ramping task " +
                    "with interval " + runMode.getInterval() + " and initial delay "
                    + runMode.getInitialDelay());
        }
    }
}
