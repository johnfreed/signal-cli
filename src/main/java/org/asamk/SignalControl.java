package org.asamk;

import org.asamk.Signal.Error;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;

import java.util.List;

/**
 * DBus interface for the org.asamk.SignalControl interface.
 * Including emitted Signals and returned Errors.
 */
public interface SignalControl extends DBusInterface {

    List<DBusPath> listAccounts();

    String link() throws SCError.Failure;

    String link(String newDeviceName) throws SCError.Failure;

    void register(
            String number, boolean voiceVerification
    ) throws SCError.Failure, SCError.InvalidNumber, SCError.RequiresCaptcha;

    void registerWithCaptcha(
            final String number, final boolean voiceVerification, final String captcha
    ) throws SCError.Failure, SCError.InvalidNumber, SCError.RequiresCaptcha;

    void verify(String number, String verificationCode) throws SCError.Failure, SCError.InvalidNumber;

    void verifyWithPin(String number, String verificationCode, String pin) throws SCError.Failure, SCError.InvalidNumber;

    String version();

    void listen(String number) throws SCError.Failure;

    interface SCError {

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
