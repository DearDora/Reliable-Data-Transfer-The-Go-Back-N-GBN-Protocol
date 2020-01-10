import java.io.*;
import java.net.*;
import java.io.PrintWriter;

class receiver{
    static final int SeqNumModulo = 32;
    static final int windowSize = 10;
    // Main function
    public static void main(String args[]) throws Exception {
        String emulatorAddress = "";
        int emulatorPortnum = 0;
        int receiverPortnum = 0;
        String outputfile = "";
        byte[] content = null;
        packet[] dataPackets = null;
        // Check argument number
        if (args.length != 4) {
            System.out.println("ERROR: Incorrect number of arguments");
            System.exit(1);
        }
        // Check argument type throw exception if the type is wrong
        try {
            emulatorAddress = args[0];
            emulatorPortnum = Integer.parseInt(args[1]);
            receiverPortnum = Integer.parseInt(args[2]);
            outputfile = args[3];
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Incorrect format of arguments");
        }
        // Create output file writers
        PrintWriter outputWriter = new PrintWriter(outputfile, "UTF-8");
        PrintWriter arrivalWriter = new PrintWriter("arrival.log", "UTF-8");
        // Create Sockets
        DatagramSocket outSocket = new DatagramSocket();
        DatagramSocket inSocket = new DatagramSocket(receiverPortnum);
        // Store data
        byte[] receivedData = new byte[512];
        byte[] sendData = new byte[512];
        int waitingSeq = 0;
        int ackSeq = 0;
        while(true){
            // Receive data and transfer to packet and get data and seq number
            DatagramPacket receivedPacket = new DatagramPacket(receivedData,receivedData.length);
            inSocket.receive(receivedPacket);
            packet receivedUDPpacket = packet.parseUDPdata(receivedPacket.getData());
            byte[] receivedPacketData = receivedUDPpacket.getData();
            arrivalWriter.println(receivedUDPpacket.getSeqNum());
            packet ACK = null;
            boolean temp = false;
            if(waitingSeq < 0){
                System.out.println("How do I get here");
                if((waitingSeq+SeqNumModulo-receivedUDPpacket.getSeqNum()) < windowSize) temp = true;
            }
            // Check sequence number of the packet
            if(receivedUDPpacket.getSeqNum() == waitingSeq){
                ackSeq = waitingSeq;
                waitingSeq = (waitingSeq+1)%SeqNumModulo;
                if(receivedUDPpacket.getType() == 1){ // Received data, write output
                    ACK = packet.createACK(ackSeq);
                    outputWriter.print(new String(receivedUDPpacket.getData()));
                }else if(receivedUDPpacket.getType() == 2){
                    ACK = packet.createEOT(ackSeq);
                    // Let sender wait for EOT
                    Thread.sleep(1000);
                }
            }else if(true) {
                ACK = packet.createACK(receivedUDPpacket.getSeqNum());
            }else {
                ACK = packet.createACK(ackSeq);
            }
            // Send ACK packet
            InetAddress address = InetAddress.getByName(emulatorAddress);
            byte ackData[] = ACK.getUDPdata();
            DatagramPacket ackPacket = new DatagramPacket(ackData,ackData.length,address,emulatorPortnum);
            outSocket.send(ackPacket);
            // End of transmission
            if(ACK.getType() == 2){
                outputWriter.print(new String(receivedUDPpacket.getData()));
                outputWriter.close();
                arrivalWriter.close();
                break;
            }
        }
    }
}