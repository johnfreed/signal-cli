package org.asamk;

import org.asamk.Signal.Error;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;

import java.util.List;

/**
 * DBus interface for the org.asamk.SignalControl interface.
 * Including emitted Signals and returned Errors.
 */
public interface SignalControl extends DBusInterface {

    List<DBusPath> listAccounts();

    String link() throws SignalControl.Error.Failure;

    String link(String newDeviceName) throws SignalControl.Error.Failure;

    void linkAndDisplay() throws SignalControl.Error.Failure;
    void linkAndDisplay(String newDeviceName) throws SignalControl.Error.Failure;

    void register(
            String number, boolean voiceVerification
    ) throws SignalControl.Error.Failure, SignalControl.Error.InvalidNumber, SignalControl.Error.RequiresCaptcha;

    void registerWithCaptcha(
            final String number, final boolean voiceVerification, final String captcha
    ) throws SignalControl.Error.Failure, SignalControl.Error.InvalidNumber, SignalControl.Error.RequiresCaptcha;

    void verify(String number, String verificationCode) throws SignalControl.Error.Failure, SignalControl.Error.InvalidNumber;

    void verifyWithPin(String number, String verificationCode, String pin) throws SignalControl.Error.Failure, SignalControl.Error.InvalidNumber;

    String version();

    void listen(String number) throws SignalControl.Error.Failure;

    interface Error {

        class Failure extends DBusExecutionException {

            public Failure(final String message) {
                super(message);
            }
        }

        class InvalidNumber extends DBusExecutionException {

            public InvalidNumber(final String message) {
                super(message);
            }
        }

        class RequiresCaptcha extends DBusExecutionException {

            public RequiresCaptcha(final String message) {
                super(message);
            }
        }
    }
}
