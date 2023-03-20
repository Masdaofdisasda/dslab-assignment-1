package dslab.dtos;

import java.util.Arrays;

public class Email {
    private String[] recipients;
    private String sender;
    private String subject = "(empty subject)";
    private String data;

    public Email(String[] recipients, String sender, String subject, String data) {
        this.recipients = recipients;
        this.sender = sender;
        this.subject = subject;
        this.data = data;
    }

    public Email() {}


    public Email(Email that) {
        this(that.recipients, that.sender, that.subject, that.data);
    }

    public String[] getRecipients() {
        return recipients;
    }
    public void setRecipients(String[] recipients) {
        this.recipients = recipients;
    }

    public void setRecipient(String recipient) {
        this.recipients = new String[]{recipient};
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public boolean isComplete() {
        return recipients != null
                && sender != null
                && subject != null
                && data != null;
    }

    public String getMissingField() {
        if (recipients == null){
            return  "recipients";
        }
        if (sender == null){
            return "sender";
        }
        if (subject == null){
            return "subject";
        }
        if (data == null){
            return "data";
        }
        return "nothing";
    }

    @Override
    public String toString() {
        return "Email{" +
                "recipients=" + Arrays.toString(recipients) +
                ", sender='" + sender + '\'' +
                ", subject='" + subject + '\'' +
                ", data='" + data + '\'' +
                '}';
    }

    public String getRecipientsForDMTP() {
        if (recipients.length == 1) {
            return recipients[0];
        }

        String result = "";

        if (recipients.length > 0) {
            StringBuilder sb = new StringBuilder();

            for (String s : recipients) {
                sb.append(s).append(",");
            }

            result = sb.deleteCharAt(sb.length() - 1).toString();
        }
        return result;
    }

}
