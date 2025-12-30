package boundary;

import control.ParkingSessionManagementController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ParkingSessionViewUI extends JPanel {

    private final ParkingSessionManagementController controller;
    private int parkingLotId;

    private JTable table;
    private DefaultTableModel model;

    public ParkingSessionViewUI(ParkingSessionManagementController controller) {
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
                new Object[]{"ID","Start Time","End Time","Vehicle","Spot","Conveyor","Amount","Rate"},0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };

        table = new JTable(model);
        add(new JScrollPane(table),BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> reload());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(refreshBtn);
        add(south,BorderLayout.SOUTH);
    }

    private void reload() {
        model.setRowCount(0);

        List<Object[]> rows = controller.getSessionsByParkingLotForManager(parkingLotId);

        for(Object[] r: rows){
            model.addRow(r);
        }
    }
}
