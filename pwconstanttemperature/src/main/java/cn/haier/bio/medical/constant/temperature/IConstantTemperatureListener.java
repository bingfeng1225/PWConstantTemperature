package cn.haier.bio.medical.constant.temperature;

public interface IConstantTemperatureListener {
    void onConstantTemperatureConnected();
    void onConstantTemperatureException();
    void onConstantTemperatureSwitchReadModel();
    void onConstantTemperatureSwitchWriteModel();
    void onConstantTemperaturePackageReceived(byte[] message);
}
