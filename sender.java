import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.lang.Object;
import java.lang.Math;
import java.net.*;
import java.util.Arrays;

class sender{
    // constants same as packet
    static final int maxDataLength = 500;
    static final int SeqNumModulo = 32;
    static final int windowSize = 10;
    static final int timeoutValue = 100;

    // Wait for the first unACKed packet
    static int waitACK(int expectSeqNum,int size, int senderPortNum, PrintWriter ackWriter) throws Exception{
        // Open socket for receiving data
        byte[] receivedData = new byte[512];
        DatagramSocket inSocket = new DatagramSocket(senderPortNum);
        inSocket.setSoTimeout(timeoutValue);
        DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);
        // Receive data
        int result = 0;
        try{
            // Loop get ACKs
            while(result < size){
                inSocket.receive(receivedPacket);
                // Get sequence number then write in file
                int seqNum = packet.parseUDPdata(receivedData).getSeqNum();
                ackWriter.println(seqNum);
                if(seqNum == expectSeqNum){
                    result ++;
                    expectSeqNum ++;
                    if(expectSeqNum >= 32) expectSeqNum-=32;
                }
            }
        }catch(SocketTimeoutException e){
            inSocket.close();
            return result;
        }finally{
            inSocket.close();
        }
        return result;
    }

    // Send a packet to destination host
    static void sendPacket(packet p, String address, int portNum, PrintWriter seqnumWriter) throws Exception{
        DatagramSocket outSocket = new DatagramSocket();
        InetAddress IPAddress = InetAddress.getByName(address);
        byte[] data = p.getUDPdata();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, portNum);
        outSocket.send(sendPacket);
        seqnumWriter.println(p.getSeqNum());
        outSocket.close();
    }

    // Open file and divide to 500 byte size data chunks and make packets
    static packet[] makePackets(byte[] data) throws Exception{
        int dataLength = data.length;
        // Create number of packets
        packet[] packets = new packet[(int)Math.ceil((double)dataLength/(double)maxDataLength)];
        int packetsLength = packets.length;
        for(int i=0; i<packetsLength; i++){
            byte[] chunk = null;
            if(dataLength >= maxDataLength){
                chunk = Arrays.copyOfRange(data,i*maxDataLength,(i+1)*maxDataLength);
                dataLength -= maxDataLength;
            }else{// End of data may not full
                chunk = Arrays.copyOfRange(data,i*maxDataLength,i*maxDataLength+dataLength);
            }
            packets[i] = packet.createPacket(i%SeqNumModulo,new String(chunk));
        }
        return packets;
    }

    // Main function
    public static void main(String args[]) throws Exception{
        // Arguments
        String emulatorAddress = "";
        int emulatorPortnum = 0;
        int senderPortnum = 0;
        String inputfile = "";
        byte[] content = null;
        packet[] dataPackets= null;
        PrintWriter seqnumWriter = new PrintWriter("seqnum.log", "UTF-8");
        PrintWriter ackWriter = new PrintWriter("ack.log", "UTF-8");
        // Check argument number
        if(args.length != 4){
            System.out.println("ERROR: Incorrect number of arguments");
            System.exit(1);
        }
        // Check argument type throw exception if the type is wrong
        try{
            emulatorAddress = args[0];
            emulatorPortnum = Integer.parseInt(args[1]);
            senderPortnum = Integer.parseInt(args[2]);
            inputfile = args[3];
        }catch(NumberFormatException e){
            System.out.println("ERROR: Incorrect format of arguments");
        }
        // Read in the file into byte
        try{
            content = Files.readAllBytes(Paths.get(inputfile));
        }catch(IOException e){
            System.out.println("ERROR: Fail to open input file");
        }
        dataPackets = makePackets(content);

        // Record number of transferred data send base position and next sequence number going to use
        int sendBase = 0;
        int nextSeqNum = 0;
        int packetsLength = dataPackets.length;
        // Keep transfer till the send base get to the end
        while(sendBase < packetsLength){
            // Record how many packets going to be sent each loop
            int sendPacketsNum = Math.min(windowSize,packetsLength-sendBase);
            // Send packets in the windows
            // Normally is the window size except the last packet may less than window size
            for(int i=sendBase; i<sendBase+sendPacketsNum; i++){
                sendPacket(dataPackets[i],emulatorAddress,emulatorPortnum,seqnumWriter);
            }
            int ACKed = waitACK(nextSeqNum,sendPacketsNum,senderPortnum,ackWriter);
            // Check the ACK number
            sendBase += ACKed;
            nextSeqNum = sendBase;
            if(nextSeqNum >= 32) nextSeqNum-=32;
        }
        // All packets received now send EOT packet
        packet EOT = packet.createEOT(nextSeqNum);
        sendPacket(EOT,emulatorAddress,emulatorPortnum,seqnumWriter);
        // Wait until get EOT ACK back
        while(true){
            int feedback = waitACK(nextSeqNum,1,senderPortnum,ackWriter);
            // Received EOT ACK, close everything
            if (feedback == 1){
                seqnumWriter.close();
                ackWriter.close();
                return;
            }
        }
    }
}