package org.asamk;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

import java.util.List;

/**
 * DBus interface for the org.asamk.Signal service.
 * Including emitted Signals and returned Errors.
 */
public interface Signal extends DBusInterface {

    long sendMessage(
            String message, List<String> attachments, String recipient
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendMessage(
            String message, List<String> attachments, List<String> recipients
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, String recipient
    ) throws Error.Failure, Error.InvalidNumber;

    long sendRemoteDeleteMessage(
            long targetSentTimestamp, List<String> recipients
    ) throws Error.Failure, Error.InvalidNumber;

    long sendGroupRemoteDeleteMessage(
            long targetSentTimestamp, byte[] groupId
    ) throws Error.Failure, Error.GroupNotFound, Error.InvalidGroupId;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String recipient
    ) throws Error.InvalidNumber, Error.Failure;

    long sendMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, List<String> recipients
    ) throws Error.InvalidNumber, Error.Failure;

    long sendNoteToSelfMessage(
            String message, List<String> attachments
    ) throws Error.AttachmentInvalid, Error.Failure;

    void sendEndSessionMessage(List<String> recipients) throws Error.Failure, Error.InvalidNumber, Error.UntrustedIdentity;

    long sendGroupMessage(
            String message, List<String> attachments, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid, Error.InvalidGroupId;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, byte[] groupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber, Error.InvalidGroupId;

    void sendContacts() throws Error.Failure, Error.UntrustedIdentity;

    void sendSyncRequest() throws Error.Failure;

    void trust(String number, String safetyNumber) throws Error.Failure, Error.InvalidNumber;

    void sendTyping(boolean typingAction, List<String> base64GroupIds, List<String> numbers) throws Error.Failure, Error.UntrustedIdentity, Error.GroupNotFound;

    String getContactName(String number) throws Error.InvalidNumber;

    void setContactName(String number, String name) throws Error.InvalidNumber;

    void setExpirationTimer(final String number, final int expiration) throws Error.InvalidNumber;

    void setContactBlocked(String number, boolean blocked) throws Error.InvalidNumber;

    void setGroupAnnounceOnly(byte[] groupId, boolean isAnnouncementGroup) throws Error.GroupNotFound;
    void setGroupAnnounceOnly(String base64GroupId, boolean isAnnouncementGroup) throws Error.GroupNotFound;
    boolean isGroupAnnounceOnly(byte[] groupId) throws Error.GroupNotFound;
    boolean isGroupAnnounceOnly(String base64GroupId) throws Error.GroupNotFound;

    void setGroupBlocked(byte[] groupId, boolean blocked) throws Error.GroupNotFound;
    void setGroupBlocked(String base64GroupId, boolean blocked) throws Error.GroupNotFound;

    List<byte[]> getGroupIds();
    // To get the group Ids in base 64 format, either use the getBaseGroupIds() method, or
    //   the getGroupIds(dummy) form, where dummy represents any string
    List<String> getGroupIdsBase64();
    List<String> getGroupIds(String dummy);

    String getGroupName(byte[] groupId);
    String getGroupName(String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    List<String> getGroupMembers(byte[] groupId);
    List<String> getGroupMembers(String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    List<String> getGroupAdminMembers(byte[] groupId) throws Error.GroupNotFound, Error.Failure;
    List<String> getGroupAdminMembers(String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    List<String> getGroupPendingMembers(byte[] groupId) throws Error.GroupNotFound, Error.Failure;
    List<String> getGroupPendingMembers(String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    List<String> getGroupRequestingMembers(byte[] groupId) throws Error.GroupNotFound, Error.Failure;
    List<String> getGroupRequestingMembers(String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    String getGroupInviteUri(byte[] groupId) throws Error.GroupNotFound, Error.Failure;
    String getGroupInviteUri(String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    byte[] updateGroup(
            byte[] groupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound, Error.InvalidGroupId;

    String updateGroup(
            String base64GroupId, String name, List<String> members, String avatar
    ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound;

    byte[] updateGroup(
            byte[] groupId,
            String name,
            String description,
            List<String> addMembers,
            List<String> removeMembers,
            List<String> addAdmins,
            List<String> removeAdmins,
            boolean resetGroupLink,
            String groupLinkState,
            String addMemberPermission,
            String editDetailsPermission,
            String avatar,
            Integer expirationTimer,
            Boolean isAnnouncementGroup
            ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound;

    String updateGroup(
            String base64GroupId,
            String name,
            String description,
            List<String> addMembers,
            List<String> removeMembers,
            List<String> addAdmins,
            List<String> removeAdmins,
            boolean resetGroupLink,
            String groupLinkState,
            String addMemberPermission,
            String editDetailsPermission,
            String avatar,
            Integer expirationTimer,
            Boolean isAnnouncementGroup
            ) throws Error.AttachmentInvalid, Error.Failure, Error.InvalidNumber, Error.GroupNotFound;

    long sendGroupMessage(
            String message, List<String> attachments, String base64GroupId
    ) throws Error.GroupNotFound, Error.Failure, Error.AttachmentInvalid;

    long sendGroupMessageReaction(
            String emoji, boolean remove, String targetAuthor, long targetSentTimestamp, String base64GroupId
    ) throws Error.GroupNotFound, Error.Failure, Error.InvalidNumber;

    boolean isRegistered(String number) throws Error.Failure, Error.InvalidNumber;

    List<Boolean> isRegistered(List<String> numbers) throws Error.Failure, Error.InvalidNumber;

    void updateProfile(
            String givenName, String familyName, String about, String aboutEmoji, String avatarPath, boolean removeAvatar
    ) throws Error.Failure;

    void updateProfile(
            String name, String about, String aboutEmoji, String avatarPath, boolean removeAvatar
    ) throws Error.Failure;

    void removePin() throws Error.Failure;

    void setPin(String registrationLockPin) throws Error.Failure;

    String version();

    String getObjectPath();

    void addDevice(String uri) throws Error.Failure;

    void removeDevice(int deviceId) throws Error.Failure;

    void unlisten() throws Error.Failure;
    void unlisten(boolean keepData) throws Error.Failure;

    void unregister() throws Error.Failure;
    void unregister(boolean keepData) throws Error.Failure;

    List<String> listNumbers();

    List<String> listDevices() throws Error.Failure;

    void updateAccount() throws Error.Failure;

    List<String> listIdentity(String number) throws Error.InvalidNumber;

    List<String> getContactNumber(final String name) throws Error.Failure;

    void quitGroup(final byte[] groupId) throws Error.GroupNotFound, Error.Failure, Error.InvalidGroupId;

    void quitGroup(final String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    boolean isContactBlocked(final String number) throws Error.InvalidNumber;

    boolean isGroupBlocked(final byte[] groupId);
    boolean isGroupBlocked(final String base64GroupId)  throws Error.GroupNotFound, Error.Failure;

    boolean isMember(final byte[] groupId);
    boolean isMember(final String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    List<String> updateMembers(final byte[] groupId, List<String>members, boolean setMemberStatus) throws Error.GroupNotFound, Error.Failure;
    List<String> updateMembers(final String base64GroupId, List<String>members, boolean setMemberStatus) throws Error.GroupNotFound, Error.Failure;

    boolean isAdmin(final byte[] groupId) throws Error.GroupNotFound, Error.Failure;
    boolean isAdmin(final String base64GroupId) throws Error.GroupNotFound, Error.Failure;

    List<String> updateAdmins(final byte[] groupId, List<String>admins, boolean setAdminStatus) throws Error.GroupNotFound, Error.Failure;
    List<String> updateAdmins(final String base64GroupId, List<String>admins, boolean setAdminStatus) throws Error.GroupNotFound, Error.Failure;

    byte[] joinGroup(final String groupLink) throws Error.Failure;

    String uploadStickerPack(String stickerPackPath) throws Error.Failure;

    class MessageReceived extends DBusSignal {

        private final long timestamp;
        private final String sender;
        private final byte[] groupId;
        private final String message;
        private final List<String> attachments;

        public MessageReceived(
                String objectpath,
                long timestamp,
                String sender,
                byte[] groupId,
                String message,
                List<String> attachments
        ) throws DBusException {
            super(objectpath, timestamp, sender, groupId, message, attachments);
            this.timestamp = timestamp;
            this.sender = sender;
            this.groupId = groupId;
            this.message = message;
            this.attachments = attachments;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSender() {
            return sender;
        }

        public byte[] getGroupId() {
            return groupId;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getAttachments() {
            return attachments;
        }
    }

    class ReceiptReceived extends DBusSignal {

        private final long timestamp;
        private final String sender;

        public ReceiptReceived(String objectpath, long timestamp, String sender) throws DBusException {
            super(objectpath, timestamp, sender);
            this.timestamp = timestamp;
            this.sender = sender;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSender() {
            return sender;
        }
    }

    class SyncMessageReceived extends DBusSignal {

        private final long timestamp;
        private final String source;
        private final String destination;
        private final byte[] groupId;
        private final String message;
        private final List<String> attachments;

        public SyncMessageReceived(
                String objectpath,
                long timestamp,
                String source,
                String destination,
                byte[] groupId,
                String message,
                List<String> attachments
        ) throws DBusException {
            super(objectpath, timestamp, source, destination, groupId, message, attachments);
            this.timestamp = timestamp;
            this.source = source;
            this.destination = destination;
            this.groupId = groupId;
            this.message = message;
            this.attachments = attachments;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getSource() {
            return source;
        }

        public String getDestination() {
            return destination;
        }

        public byte[] getGroupId() {
            return groupId;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getAttachments() {
            return attachments;
        }
    }

    interface Error {

        class AttachmentInvalid extends DBusExecutionException {

            public AttachmentInvalid(final String message) {
                super(message);
            }
        }

        class Failure extends DBusExecutionException {

            public Failure(final String message) {
                super(message);
            }
        }

        class GroupNotFound extends DBusExecutionException {

            public GroupNotFound(final String message) {
                super(message);
            }
        }

        class InvalidGroupId extends DBusExecutionException {

            public InvalidGroupId(final String message) {
                super(message);
            }
        }

        class InvalidNumber extends DBusExecutionException {

            public InvalidNumber(final String message) {
                super(message);
            }
        }

        class UntrustedIdentity extends DBusExecutionException {

            public UntrustedIdentity(final String message) {
                super(message);
            }
        }
    }
}
