package boundary;

/** External actor: SMS provider */
public interface SmsGatewayPort {
    void sendSms(String phoneNumber, String message);
}