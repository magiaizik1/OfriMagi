package simulation;

import boundary.SmsGatewayPort;

public class ConsoleSmsGatewayMock implements SmsGatewayPort {

    @Override
    public void sendSms(String phoneNumber, String message) {
        System.out.println("📩 [MOCK-SMS] To: " + phoneNumber + " | " + message);
    }
}