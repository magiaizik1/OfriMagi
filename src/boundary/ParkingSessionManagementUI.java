package boundary;

import control.ParkingSessionManagementController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Boundary – ניהול סשן חניה
 * מאפשר:
 * 1. התחלת חניה
 * 2. סיום חניה
 * 3. הצגת משך חניה
 * 4. יצירת קבלה (Receipt)
 */
public class ParkingSessionManagementUI extends JPanel {

    private final ParkingSessionManagementController controller;
    private int parkingLotId;

    private JTable table;
    private DefaultTableModel model;

    public ParkingSessionManagementUI(ParkingSessionManagementController controller) {
        this.controller = controller;
        initUI();
    }

    public void setParkingLotId(int parkingLotId) {
        this.parkingLotId = parkingLotId;
        reload();
    }

    private void initUI() {
        setLayout(new BorderLayout(10,10));

        model = new DefaultTableModel(
                new Object[]{"ID","Start Time","End Time","Vehicle","Spot","Conveyor"},0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };

        table = new JTable(model);
        add(new JScrollPane(table),BorderLayout.CENTER);

        JButton startBtn   = new JButton("Start Parking");
        JButton endBtn     = new JButton("End Parking");
        JButton durationBtn= new JButton("Show Duration");
        JButton receiptBtn = new JButton("Generate Receipt");

        startBtn.addActionListener(e -> startParking());
        endBtn.addActionListener(e -> endParking());
        durationBtn.addActionListener(e -> showDuration());
        receiptBtn.addActionListener(e -> generateReceipt());

        JPanel south = new JPanel(new GridLayout(1,4,8,8));
        south.add(startBtn);
        south.add(endBtn);
        south.add(durationBtn);
        south.add(receiptBtn);

        add(south,BorderLayout.SOUTH);
    }

    private void reload() {
        model.setRowCount(0);
        List<Object[]> rows = controller.getSessionsByParkingLot(parkingLotId);
        for(Object[] r: rows){
            model.addRow(r);
        }
    }

    private Integer getSelectedSessionId() {
        int row = table.getSelectedRow();
        if(row==-1) return null;
        return (Integer) model.getValueAt(row,0);
    }

    private void startParking() {
        try{
            int vehicleId = Integer.parseInt(JOptionPane.showInputDialog("Vehicle ID:"));
            int spotId    = Integer.parseInt(JOptionPane.showInputDialog("Parking Spot ID:"));
            int conveyorId= Integer.parseInt(JOptionPane.showInputDialog("Conveyor ID:"));

            controller.startParkingSession(parkingLotId,vehicleId,spotId,conveyorId);
            reload();
        }catch(Exception ex){
            JOptionPane.showMessageDialog(this,ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void endParking() {
        Integer id = getSelectedSessionId();
        if(id==null) return;
        controller.endParkingSession(id);
        reload();
    }

    private void showDuration() {
        Integer id = getSelectedSessionId();
        if(id==null) return;
        long minutes = controller.getParkingDurationMinutes(id);
        JOptionPane.showMessageDialog(this,
                "Parking duration: "+minutes+" minutes");
    }

    private void generateReceipt() {
        Integer id = getSelectedSessionId();
        if(id==null) return;
        controller.generateReceipt(id);
        JOptionPane.showMessageDialog(this,"Receipt generated.");
    }
}