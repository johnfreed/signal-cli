package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.signal.OutputWriter;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.manager.Manager;

import java.util.List;

public interface MultiLocalCommand extends LocalCommand {

    void handleCommand(Namespace ns, List<Manager> m, SignalCreator c, OutputWriter outputWriter) throws CommandException;
    void handleCommand(Namespace ns, Manager m, SignalCreator c, OutputWriter outputWriter) throws CommandException;

}
