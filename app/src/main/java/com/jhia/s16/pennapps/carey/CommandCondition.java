package com.jhia.s16.pennapps.carey;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by He on 1/23/2016.
 */
public abstract class CommandCondition {

    private String[] commands = null;

    protected CommandCondition(int wordCount) {
        commands = new String[wordCount];
    }

    public abstract boolean isCommandSatisfied();

    protected String[] getCommands() {
        return commands;
    }

    public void setCommand(int i, String command) {
        commands[i] = command;
    }

}
