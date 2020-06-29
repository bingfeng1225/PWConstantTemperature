package cn.haier.bio.medical.constant.temperature;

/***
 * 超低温变频、T系列、双系统主控板通讯
 *
 */
public class ConstantTemperatureManager {
    private ConstantTemperatureSerialPort serialPort;
    private static ConstantTemperatureManager manager;

    public static ConstantTemperatureManager getInstance() {
        if (manager == null) {
            synchronized (ConstantTemperatureManager.class) {
                if (manager == null)
                    manager = new ConstantTemperatureManager();
            }
        }
        return manager;
    }

    private ConstantTemperatureManager() {

    }

    public void init(String path) {
        if (this.serialPort == null) {
            this.serialPort = new ConstantTemperatureSerialPort();
            this.serialPort.init(path);
        }
    }

    public void enable() {
        if (null != this.serialPort) {
            this.serialPort.enable();
        }
    }

    public void disable() {
        if (null != this.serialPort) {
            this.serialPort.disable();
        }
    }

    public void release() {
        if (null != this.serialPort) {
            this.serialPort.release();
            this.serialPort = null;
        }
    }

    public void sendData(byte[] data) {
        if (null != this.serialPort) {
            this.serialPort.sendData(data);
        }
    }

    public void changeListener(IConstantTemperatureListener listener) {
        if (null != this.serialPort) {
            this.serialPort.changeListener(listener);
        }
    }
}

