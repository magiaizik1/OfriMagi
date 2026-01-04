package boundary;

import javax.swing.*;
import java.awt.*;

public class RoleSelectUI extends JFrame {

    public interface RoleCallback {
        void onAdmin();
        void onClient();
    }

    public RoleSelectUI(RoleCallback cb) {
        super("Select Role");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(320, 140);
        setLocationRelativeTo(null);

        JButton adminBtn = new JButton("Admin");
        JButton clientBtn = new JButton("Client");

        adminBtn.addActionListener(e -> cb.onAdmin());
        clientBtn.addActionListener(e -> cb.onClient());

        JPanel p = new JPanel(new GridLayout(2, 1, 10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(adminBtn);
        p.add(clientBtn);

        setContentPane(p);
    }
}
