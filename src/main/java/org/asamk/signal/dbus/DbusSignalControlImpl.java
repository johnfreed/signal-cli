package org.asamk.signal.dbus;

import org.asamk.Signal;
import org.asamk.SignalControl;
import org.asamk.Signal.Error;
import org.asamk.signal.App;
import org.asamk.signal.BaseConfig;
import org.asamk.signal.DbusConfig;
import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.JsonWriter;
import org.asamk.signal.OutputWriter;
import org.asamk.signal.PlainTextWriter;
import org.asamk.signal.commands.DaemonCommand;
import org.asamk.signal.commands.SignalCreator;
import org.asamk.signal.commands.UpdateGroupCommand;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.commands.exceptions.UnexpectedErrorException;
import org.asamk.signal.commands.exceptions.UserErrorException;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.AttachmentInvalidException;
import org.asamk.signal.manager.AvatarStore;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.NotMasterDeviceException;
import org.asamk.signal.manager.NotRegisteredException;
import org.asamk.signal.manager.PathConfig;
import org.asamk.signal.manager.ProvisioningManager;
import org.asamk.signal.manager.RegistrationManager;
import org.asamk.signal.manager.StickerPackInvalidException;
import org.asamk.signal.manager.UserAlreadyExists;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.manager.api.TypingAction;
import org.asamk.signal.manager.config.ServiceConfig;
import org.asamk.signal.manager.config.ServiceEnvironment;
import org.asamk.signal.manager.groups.GroupId;
import org.asamk.signal.manager.groups.GroupIdFormatException;
import org.asamk.signal.manager.groups.GroupInviteLinkUrl;
import org.asamk.signal.manager.groups.GroupNotFoundException;
import org.asamk.signal.manager.groups.LastGroupAdminException;
import org.asamk.signal.manager.groups.NotAGroupMemberException;
import org.asamk.signal.manager.storage.groups.GroupInfo;
import org.asamk.signal.manager.storage.identities.IdentityInfo;
import org.asamk.signal.util.ErrorUtils;
import org.asamk.signal.util.Hex;
import org.asamk.signal.util.Util;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

import static org.asamk.signal.util.Util.getLegacyIdentifier;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DbusSignalControlImpl implements org.asamk.SignalControl {

    private static SignalCreator c;
    private static Function<Manager, Thread> newManagerRunner;

    private static List<Pair<Manager, Thread>> receiveThreads = new ArrayList<>();
    private static Object stopTrigger = new Object();
    private static String objectPath;

    public static RegistrationManager registrationManager;
    public static ProvisioningManager provisioningManager;

    private final static Logger logger = LoggerFactory.getLogger(DbusSignalControlImpl.class);

    public DbusSignalControlImpl(
            final SignalCreator c, final Function<Manager, Thread> newManagerRunner, final String objectPath
    ) {
        this.c = c;
        this.newManagerRunner = newManagerRunner;
        this.objectPath = objectPath;
    }

    public static void addManager(Manager m) {
        var thread = newManagerRunner.apply(m);
        if (thread == null) {
            return;
        }
        synchronized (receiveThreads) {
            receiveThreads.add(new Pair<>(m, thread));
        }
    }

    public void run() {
        synchronized (stopTrigger) {
            try {
                stopTrigger.wait();
            } catch (InterruptedException ignored) {
            }
        }

        synchronized (receiveThreads) {
            for (var t : receiveThreads) {
                t.second().interrupt();
            }
        }
        while (true) {
            final Thread thread;
            synchronized (receiveThreads) {
                if (receiveThreads.size() == 0) {
                    break;
                }
                var pair = receiveThreads.remove(0);
                thread = pair.second();
            }
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return objectPath;
    }

    @Override
    public void register(
            final String number, final boolean voiceVerification
    ) {
        registerWithCaptcha(number, voiceVerification, null);
    }

    @Override
    public void registerWithCaptcha(
            final String number, final boolean voiceVerification, final String captcha
    ) {
        try  {
            try {
                registrationManager = c.getNewRegistrationManager(number);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                throw new Error.Failure(e.getMessage());
            }
            registrationManager.register(voiceVerification, captcha);
        } catch (CaptchaRequiredException e) {
            try {
                registrationManager.close();
            } catch (IOException f) {
                throw new SCError.Failure(f.getClass().getSimpleName() + " " + f.getMessage());
            }
            String message = captcha == null ? "Captcha required for verification. Get one from https://signalcaptchas.org/registration/generate.html"
                            : "Invalid captcha given. Get one from https://signalcaptchas.org/registration/generate.html";
            throw new SCError.RequiresCaptcha(message);
        } catch (IOException e) {
            throw new SCError.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    @Override
    public void verify(final String number, final String verificationCode) {
        verifyWithPin(number, verificationCode, null);
    }

    @Override
    public void verifyWithPin(final String number, final String verificationCode, final String pin)
    {
        try {
            final Manager manager = registrationManager.verifyAccount(verificationCode, pin);
            logger.info("Registration of " + number + " verified");
            manager.close();
            registrationManager.close();
        } catch (IOException | KeyBackupSystemNoDataException | KeyBackupServicePinException e) {
            throw new SCError.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
        listen(number);
    }

    @Override
    public String link() {
        return link("cli");
    }

    @Override
    public String link(final String newDeviceName) {
        try {
            provisioningManager = c.getNewProvisioningManager();
            final URI deviceLinkUri = provisioningManager.getDeviceLinkUri();
            new Thread(() -> {
                try {
                    Manager manager = provisioningManager.finishDeviceLink(newDeviceName);
                    logger.info("Linking of " + newDeviceName + " successful");
                    manager.close();
                } catch (TimeoutException e) {
                    throw new SCError.Failure(e.getClass().getSimpleName() + ": Link request timed out, please try again.");
                } catch (IOException e) {
                    throw new SCError.Failure(e.getClass().getSimpleName() + ": Link request error: " + e.getMessage());
                } catch (UserAlreadyExists e) {
                    throw new SCError.Failure(e.getClass().getSimpleName() + ": The user "
                            + e.getUsername()
                            + " already exists\nDelete \""
                            + e.getFileName()
                            + "\" before trying again.");
                    }
            }).start();
            return deviceLinkUri.toString();
        } catch (TimeoutException | IOException e) {
            throw new SCError.Failure(e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }


    public String version() {
        return BaseConfig.PROJECT_VERSION;
    }

    @Override
    public void listen(String number) {
        try {
            //make sure user is registered
            List<Manager> mList = listManagers();

            //we know at least one manager exists, so get settings info from it
            Manager mgr = listManagers().get(0);
            PathConfig pathConfig = mgr.getPathConfig();
            File settingsPath = new File(pathConfig.getDataPath().getParent());
            List<String> usernames = mgr.getAllLocalUsernames(settingsPath);
            if (!usernames.contains(number)) {
                throw new Error.Failure("Listen: " + number + " is not registered.");
            }
            String objectPath = DbusConfig.getObjectPath(number);
            DBusInterface iface = new org.asamk.signal.dbus.DbusSignalImpl(mgr, objectPath);
            DBusConnection.DBusBusType busType = DbusSignalImpl.getDbusType(iface);

            //seems to be no way to test for service environment
            ServiceEnvironment serviceEnvironment = ServiceEnvironment.LIVE;

            //create new manager for this number
            final Manager m = loadManager(number, settingsPath, serviceEnvironment);
            this.addManager(m);

            final var thread = new Thread(() -> {
                try {
                    OutputWriter outputWriter = DaemonCommand.getOutputWriter();
                    boolean ignoreAttachments = false;
                    DBusConnection conn = DBusConnection.getConnection(busType);
                    while (!Thread.interrupted()) {
                        try {
                            final var receiveMessageHandler = outputWriter instanceof JsonWriter
                                    ? new JsonDbusReceiveMessageHandler(m, (JsonWriter) outputWriter, conn, objectPath)
                                            : new DbusReceiveMessageHandler(m, (PlainTextWriter) outputWriter, conn, objectPath);
                            m.receiveMessages(1, TimeUnit.HOURS, false, ignoreAttachments, receiveMessageHandler);
                            break;
                        } catch (IOException f) {
                            logger.warn("Receiving messages failed, retrying", f);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                } catch (DBusException e) {
                    throw new Error.Failure(e.getClass().getSimpleName() + " Listen error: " + e.getMessage());
                }
            });
        } catch (CommandException e) {
            logger.warn("Ignoring {}: {}", number, e.getMessage());
            throw new Error.Failure(e.getClass().getSimpleName() + " Listen error: " + e.getMessage());
        }
    }

    private List<Manager> listManagers(){
        synchronized (receiveThreads) {
            return receiveThreads.stream()
                    .map(Pair::first)
                    .collect(Collectors.toList());
        }
    }

    private Manager loadManager(
            final String username, final File settingsPath, final ServiceEnvironment serviceEnvironment
    ) throws CommandException {
        Manager manager;
        try {
            manager = Manager.init(username, settingsPath, serviceEnvironment, BaseConfig.USER_AGENT);
        } catch (NotRegisteredException e) {
            throw new UserErrorException("User " + username + " is not registered.");
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

    @Override
    public List<DBusPath> listAccounts() {
        synchronized (receiveThreads) {
            return receiveThreads.stream()
                    .map(Pair::first)
                    .map(Manager::getUsername)
                    .map(u -> new DBusPath(DbusConfig.getObjectPath(u)))
                    .collect(Collectors.toList());
        }
    }
}
