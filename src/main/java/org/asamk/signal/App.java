package org.asamk.signal;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import org.asamk.Signal;
import org.asamk.Signal.Error;
import org.asamk.signal.commands.Command;
import org.asamk.signal.commands.Commands;
import org.asamk.signal.commands.DbusCommand;
import org.asamk.signal.commands.ExtendedDbusCommand;
import org.asamk.signal.commands.LocalCommand;
import org.asamk.signal.commands.MultiLocalCommand;
import org.asamk.signal.commands.ProvisioningCommand;
import org.asamk.signal.commands.RegistrationCommand;
import org.asamk.signal.commands.SignalCreator;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotRegisteredException;
import org.asamk.signal.manager.PathConfig;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.util.IOUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.File;
import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.sourceforge.argparse4j.DefaultSettings.VERSION_0_9_0_DEFAULT_SETTINGS;

public class App {

    private final static Logger logger = LoggerFactory.getLogger(App.class);

    private final Namespace ns;

    static ArgumentParser buildArgumentParser() {
        var parser = ArgumentParsers.newFor("signal-cli", VERSION_0_9_0_DEFAULT_SETTINGS)
                .includeArgumentNamesAsKeysInResult(true)
                .build()
                .defaultHelp(true)
                .description("Commandline interface for Signal.")
                .version(BaseConfig.PROJECT_NAME + " " + BaseConfig.PROJECT_VERSION);

        parser.addArgument("-v", "--version").help("Show package version.").action(Arguments.version());
        parser.addArgument("--verbose")
                .help("Raise log level and include lib signal logs.")
                .action(Arguments.storeTrue());
        parser.addArgument("--config")
                .help("Set the path, where to store the config (Default: $XDG_DATA_HOME/signal-cli , $HOME/.local/share/signal-cli).");

        parser.addArgument("-u", "--username").help("Specify your phone number, that will be your identifier.");

        var mut = parser.addMutuallyExclusiveGroup();
        mut.addArgument("--dbus").help("Make request via user dbus.").action(Arguments.storeTrue());
        mut.addArgument("--dbus-system").help("Make request via system dbus.").action(Arguments.storeTrue());

        parser.addArgument("-o", "--output")
                .help("Choose to output in plain text or JSON")
                .type(Arguments.enumStringType(OutputType.class))
                .setDefault(OutputType.PLAIN_TEXT);

        parser.addArgument("--service-environment")
                .help("Choose the server environment to use.")
                .type(Arguments.enumStringType(ServiceEnvironmentCli.class))
                .setDefault(ServiceEnvironmentCli.LIVE);

        var subparsers = parser.addSubparsers().title("subcommands").dest("command");

        Commands.getCommandSubparserAttachers().forEach((key, value) -> {
            var subparser = subparsers.addParser(key);
            value.attachToSubparser(subparser);
        });

        return parser;
    }

    public App(final Namespace ns) {
        this.ns = ns;
    }

    public void init() throws CommandException {
        var outputType = ns.<OutputType>get("output");
        var outputWriter = outputType == OutputType.JSON
                ? new JsonWriterImpl(System.out)
                : new PlainTextWriterImpl(System.out);

        var commandKey = ns.getString("command");
        var command = Commands.getCommand(commandKey, outputWriter);
        if (command == null) {
            throw new UserErrorException("Command not implemented!");
        }

        if (!command.getSupportedOutputTypes().contains(outputType)) {
            throw new UserErrorException("Command doesn't support output type " + outputType.toString());
        }

        String username = ns.getString("username");

        final var useDbus = ns.getBoolean("dbus");
        final var useDbusSystem = ns.getBoolean("dbus-system");
        if (useDbus || useDbusSystem) {
            // If username is null, it will connect to the default object path
            initDbusClient(command, username, useDbusSystem);
            return;
        }

        final File settingsPath;
        var config = ns.getString("config");
        if (config != null) {
            settingsPath = new File(config);
        } else {
            settingsPath = getDefaultDataPath();
        }

        final var serviceEnvironmentCli = ns.<ServiceEnvironmentCli>get("service-environment");
        ServiceEnvironment serviceEnvironment = serviceEnvironmentCli == ServiceEnvironmentCli.LIVE
                ? ServiceEnvironment.LIVE
                : ServiceEnvironment.SANDBOX;

        if (!ServiceConfig.getCapabilities().isGv2()) {
            logger.warn("WARNING: Support for new group V2 is disabled,"
                    + " because the required native library dependency is missing: libzkgroup");
        }

        if (!ServiceConfig.isSignalClientAvailable()) {
            throw new UserErrorException("Missing required native library dependency: libsignal-client");
        }

        if (command instanceof ProvisioningCommand) {
            if (username != null) {
                throw new UserErrorException("You cannot specify a username (phone number) when linking");
            }

            handleProvisioningCommand((ProvisioningCommand) command, settingsPath, serviceEnvironment);
            return;
        }

        if (command instanceof MultiLocalCommand) {
            List<String> usernames = new ArrayList<>();
            if (username == null) {
                //anonymous mode
                handleMultiLocalCommand((MultiLocalCommand) command, settingsPath, serviceEnvironment, usernames);
            } else {
                //single-user mode
                handleMultiLocalCommand((MultiLocalCommand) command, settingsPath, serviceEnvironment, username);
            }
            return;
        }

        if (username == null) {
            var usernames = Manager.getAllLocalUsernames(settingsPath);
            if (usernames.size() == 0) {
                throw new UserErrorException("No local users found, you first need to register or link an account");
            } else if (usernames.size() > 1) {
                throw new UserErrorException(
                        "Multiple users found, you need to specify a username (phone number) with -u");
            }

            username = usernames.get(0);
        } else if (!PhoneNumberFormatter.isValidNumber(username, null)) {
            throw new UserErrorException("Invalid username (phone number), make sure you include the country code.");
        }

        if (command instanceof RegistrationCommand) {
            handleRegistrationCommand((RegistrationCommand) command, username, settingsPath, serviceEnvironment);
            return;
        }

        if (!(command instanceof LocalCommand)) {
            throw new UserErrorException("Command only works via dbus");
        }

        handleLocalCommand((LocalCommand) command, username, settingsPath, serviceEnvironment);
    }

    private void handleProvisioningCommand(
            final ProvisioningCommand command, final File settingsPath, final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        var pm = ProvisioningManager.init(settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
        command.handleCommand(ns, pm);
    }

    private void handleRegistrationCommand(
            final RegistrationCommand command,
            final String username,
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        final RegistrationManager manager;
        try {
            manager = RegistrationManager.init(username, settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
        } catch (Throwable e) {
            throw new UnexpectedErrorException("Error loading or creating state file: "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")");
        }
        try (var m = manager) {
            command.handleCommand(ns, m);
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleLocalCommand(
            final LocalCommand command,
            final String username,
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        Manager m = loadManager(username, settingsPath, serviceEnvironment);
        command.handleCommand(ns, m);
        try {
            m.close();;
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    private void handleMultiLocalCommand(
            final MultiLocalCommand command,
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment,
            final List<String> usernames
    ) throws CommandException {
        SignalCreator c = new SignalCreator() {

            @Override
            public File getSettingsPath() {
                return settingsPath;
            }

            @Override
            public ServiceEnvironment getServiceEnvironment() {
                return serviceEnvironment;
            }

            @Override
            public ProvisioningManager getNewProvisioningManager() {
                return ProvisioningManager.init(settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
            }

            @Override
            public RegistrationManager getNewRegistrationManager(String username) throws IOException {
                return RegistrationManager.init(username, settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
            }
        };

        final var managers = new ArrayList<Manager>();
        command.handleCommand(ns, managers, c);

        for (var m : managers) {
            try {
                m.close();
            } catch (IOException e) {
                logger.warn("Cleanup failed", e);
            }
        }
    }

    private void handleMultiLocalCommand(
            final MultiLocalCommand command,
            final File settingsPath,
            final ServiceEnvironment serviceEnvironment,
            final String username
    ) throws CommandException {

        SignalCreator c = new SignalCreator() {

            @Override
            public File getSettingsPath() {
                return settingsPath;
            }

            @Override
            public ServiceEnvironment getServiceEnvironment() {
                return serviceEnvironment;
            }

            @Override
            public ProvisioningManager getNewProvisioningManager() {
                return ProvisioningManager.init(settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
            }

            @Override
            public RegistrationManager getNewRegistrationManager(String username) throws IOException {
                return RegistrationManager.init(username, settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
            }

        };

        Manager manager = null;
        try {
            manager = loadManager(username, settingsPath, serviceEnvironment);
        } catch (CommandException e) {
            logger.warn("Ignoring {}: {}", username, e.getMessage());
        }

        command.handleCommand(ns, manager, c);

        try {
            manager.close();
        } catch (IOException e) {
            logger.warn("Cleanup failed", e);
        }
    }

    public static Manager loadManager(
            final String username, final File settingsPath, final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        Manager manager;
        try {
            manager = Manager.init(username, settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
        } catch (NotRegisteredException e) {
            throw new UserErrorException("User " + username + " is not registered.");
        } catch (OverlappingFileLockException e) {
            throw new UserErrorException("User " + username + " is already listening.");
        } catch (Throwable e) {
            logger.debug("Loading state file failed", e);
            throw new UnexpectedErrorException("Error loading state file for user "
                    + username
                    + ": "
                    + e.getMessage()
                    + " ("
                    + e.getClass().getSimpleName()
                    + ")");
        }

        try {
            manager.checkAccountState();
        } catch (IOException e) {
            throw new UnexpectedErrorException("Error while checking account " + username + ": " + e.getMessage());
        }

        return manager;
    }

    private void initDbusClient(
            final Command command, final String username, final boolean systemBus
    ) throws CommandException {
        try {
            DBusConnection.DBusBusType busType;
            if (systemBus) {
                busType = DBusConnection.DBusBusType.SYSTEM;
            } else {
                busType = DBusConnection.DBusBusType.SESSION;
            }
            try (var dBusConn = DBusConnection.getConnection(busType)) {
                var ts = dBusConn.getRemoteObject(DbusConfig.getBusname(),
                        DbusConfig.getObjectPath(username),
                        Signal.class);

                handleCommand(command, ts, dBusConn);
            }
        } catch (DBusException | IOException e) {
            logger.error("Dbus client failed", e);
            throw new UnexpectedErrorException("Dbus client failed");
        }
    }

    private void handleCommand(Command command, Signal ts, DBusConnection dBusConn) throws CommandException {
        if (command instanceof ExtendedDbusCommand) {
            ((ExtendedDbusCommand) command).handleCommand(ns, ts, dBusConn);
        } else if (command instanceof DbusCommand) {
            ((DbusCommand) command).handleCommand(ns, ts);
        } else {
            throw new UserErrorException("Command is not yet implemented via dbus");
        }
    }

    /**
     * @return the default data directory to be used by signal-cli.
     */
    private static File getDefaultDataPath() {
        return new File(IOUtils.getDataHomeDir(), "signal-cli");
    }
}
