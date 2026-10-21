import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Sonda AWT do tipo de janela (#434): um JFrame transparente e sem decoração por
 * tipo, como o ComposeWindow da HUD. Imprime clique, tecla e foco; posição,
 * profundidade e override-redirect se leem por fora com xwininfo.
 *
 * Roda com o JDK do sistema, sem compilar: java tools/cosmic/AwtWindowTypeProbe.java [segundos]
 */
public class AwtWindowTypeProbe {
    public static void main(String[] args) throws Exception {
        long seconds = args.length > 0 ? Long.parseLong(args[0]) : 30;
        SwingUtilities.invokeAndWait(() -> {
            Window.Type[] types = {Window.Type.NORMAL, Window.Type.UTILITY, Window.Type.POPUP};
            for (int index = 0; index < types.length; index++) {
                open(types[index], 100 + index * 320, 250);
            }
        });
        Thread.sleep(seconds * 1000);
        System.exit(0);
    }

    private static void open(Window.Type type, int x, int y) {
        JFrame frame = new JFrame("awt-" + type);
        try {
            frame.setType(type);
        } catch (RuntimeException error) {
            System.out.println(type + " setType falhou: " + error);
            return;
        }
        frame.setUndecorated(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setAlwaysOnTop(true);
        frame.setBounds(x, y, 300, 160);
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(new Color(255, 120, 0, 220));
                graphics.fillRoundRect(20, 20, 260, 120, 40, 40);
            }
        };
        panel.setOpaque(false);
        panel.setFocusable(true);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                System.out.println(type + " mousePressed " + event.getPoint());
                panel.requestFocusInWindow();
            }
        });
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                System.out.println(type + " keyPressed " + KeyEvent.getKeyText(event.getKeyCode()));
            }
        });
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                System.out.println(type + " windowGainedFocus");
            }
        });
        frame.setContentPane(panel);
        frame.setVisible(true);
        System.out.println(type + " pedido " + x + "," + y + " focável=" + frame.isFocusableWindow());
    }
}
