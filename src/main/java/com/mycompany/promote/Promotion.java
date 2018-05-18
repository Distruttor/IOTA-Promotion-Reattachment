/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.promote;

import cfb.pearldiver.PearlDiverLocalPoW;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import jota.IotaAPI;
import jota.dto.response.GetBundleResponse;
import jota.dto.response.GetNewAddressResponse;
import jota.dto.response.GetNodeInfoResponse;
import jota.dto.response.GetTransactionsToApproveResponse;
import jota.error.ArgumentException;
import jota.model.Transaction;
import jota.model.Transfer;
import jota.utils.TrytesConverter;

/**
 *
 * @author mirko
 */
public class Promotion {
    public static void main(String [] args) {
        IotaAPI iota = new IotaAPI.Builder()
        .protocol("http")
        .host("62.75.142.71")
        .port("14265")
        .localPoW(new PearlDiverLocalPoW())
        .build();
        
        GetNodeInfoResponse response = iota.getNodeInfo(); 
        
        System.out.println("Response: " + response);       
        
        String seed = "LZAXASHEXEBOQPEMCFNHVRQJDWNSHNIEVABQJTETROFHXNKUPIPKPRIBH9MOFCXQAWEHZB9XNBZZGBVZJ";
        int security = 2;
        int depth = 3;
        int minMagnitudeWeight = 14;
        
        String command = args[0];
        
        if(command.equals("-p")) {
            int timesPromote = Integer.parseInt(args[1]);
            String transactionToPromote = args[2];

            System.out.println("Starting promotion of transaction " + transactionToPromote);
            GetNewAddressResponse addressResponse = null;
            String lastHash = null;
            for(int j = 0; j < timesPromote; j++) {
                //Generate random index address
                Random r = new Random();
                int addressIndex = r.nextInt(10000);

                //Generate a new address
                try {
                    addressResponse = iota.getNewAddress(seed, security, addressIndex, true, 1, true);
                    System.out.println("Address: " + addressResponse.getAddresses().get(0) + "\n");
                } catch (ArgumentException ex) {
                    System.out.println("Error: " + ex);
                }

                Transfer transfer = new Transfer(addressResponse.getAddresses().get(0), 0, TrytesConverter.toTrytes("Promotion transaction"), "MIRKO");
                List<Transfer> transfers = new ArrayList<>();
                transfers.add(transfer);

                List <String> preparedTransfers = null;       

                try {
                    preparedTransfers = iota.prepareTransfers(seed, security, transfers, null, null, true);
                    System.out.println("Prepared transfers: " + preparedTransfers + "\n");
                } catch (ArgumentException ex) {
                    System.out.println("Error: " + ex.getMessage());
                }

                //List of transactions that compose the bundle
                List<Transaction> transactions = new ArrayList<>();

                for(String tran: preparedTransfers) {
                    Transaction t = new Transaction(tran);
                    transactions.add(t);
                }

                GetTransactionsToApproveResponse transactionsToApproveResponse = iota.getTransactionsToApprove(depth);
                //Set trunk and branch transaction
                String branchTransaction = transactionsToApproveResponse.getBranchTransaction();
                String trunkTransaction = transactionsToApproveResponse.getTrunkTransaction();


//                String milestone = response.getLatestMilestone();
//                System.out.println("Milestone: " + milestone);

                //The tail transaction has trunk and branch set according to the value discovered before
                transactions.get(0).setBranchTransaction(transactionToPromote);
                if(j == 0) {
                    transactions.get(0).setTrunkTransaction(trunkTransaction);
                } else {
                    transactions.get(0).setTrunkTransaction(lastHash);
                }
                
                long maxTimestampValue =  (long) (Math.pow(3, 27) - 1) / 2;
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                transactions.get(0).setAttachmentTimestampLowerBound(0);
                transactions.get(0).setAttachmentTimestampUpperBound(maxTimestampValue);
                transactions.get(0).setAttachmentTimestamp(timestamp.getTime());


                String trytes = transactions.get(0).toTrytes();
                //Proof of Work
                PearlDiverLocalPoW pow = new PearlDiverLocalPoW();
                String powRis = pow.performPoW(trytes, minMagnitudeWeight);

                //Remove transaction from the list
                transactions.remove(0);
                
                Transaction lastTransaction = new Transaction(powRis);
                //Add the transaction updated with correct nonce
                transactions.add(0, lastTransaction);
                lastHash = lastTransaction.getHash();
                //Broadcast the transaction to the Tangle
                try {
                    iota.broadcastAndStore(powRis);
                    System.out.println("Transaction attached to the Tangle!\n");
                    System.out.println(transactions.get(0));
                } catch (ArgumentException ex) {
                    Logger.getLogger(Promotion.class.getName()).log(Level.SEVERE, null, ex);
                }


                //Other transactions
                //Set branch transaction to the trunk transaction value founded
                // Set trunk transaction to the hash of the next transaction in the bundle
                for(int i = 1; i < transactions.size(); i++) {
                    transactions.get(i).setBranchTransaction(trunkTransaction);
                    transactions.get(i).setTrunkTransaction(transactions.get(i-1).getHash());
                    transactions.get(i).setAttachmentTimestampLowerBound(0);
                    transactions.get(i).setAttachmentTimestampUpperBound(maxTimestampValue);
                    transactions.get(i).setAttachmentTimestamp(timestamp.getTime());

                    trytes = transactions.get(i).toTrytes();
                    //Proof of Work
                    powRis = pow.performPoW(trytes, minMagnitudeWeight);
                    transactions.remove(i);
                    transactions.add(i, new Transaction(powRis));
                    try {
                        iota.broadcastAndStore(powRis);
                        System.out.println("Transaction attached to the Tangle!\n");
                        System.out.println(transactions.get(i));
                    } catch (ArgumentException ex) {
                        Logger.getLogger(Promotion.class.getName()).log(Level.SEVERE, null, ex);

                    }
                }
            }     
        } else if(command.equals("-r")) { //Reattach transaction
            String tailTransactionHash = args[1];
            GetBundleResponse  bundleResponse = null;
            //Get the Bundle of the given transaction
            try {
                bundleResponse = iota.getBundle(tailTransactionHash);
            } catch (ArgumentException ex) {
                Logger.getLogger(Promotion.class.getName()).log(Level.SEVERE, null, ex);
            }
            List<Transaction> transactions = new ArrayList<>();
            transactions = bundleResponse.getTransactions();
            System.out.println("Starting reattachment of bundle");
            
            //Get new tips to approve
            GetTransactionsToApproveResponse transactionsToApproveResponse = iota.getTransactionsToApprove(depth);
            //Set trunk and branch transaction
            String branchTransaction = transactionsToApproveResponse.getBranchTransaction();
            String trunkTransaction = transactionsToApproveResponse.getTrunkTransaction();
            
            Collections.reverse(transactions);
            //Set new timestamp to transactions
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            transactions.get(0).setAttachmentTimestamp(timestamp.getTime());
            transactions.get(0).setTrunkTransaction(trunkTransaction);
            transactions.get(0).setBranchTransaction(branchTransaction);
            transactions.get(0).setAttachmentTimestamp(timestamp.getTime());
            
            String trytes = transactions.get(0).toTrytes();
            //Proof of Work again
            PearlDiverLocalPoW pow = new PearlDiverLocalPoW();
            String powRis = pow.performPoW(trytes, minMagnitudeWeight);
            
            try {
                iota.broadcastAndStore(powRis);
                if(transactions.size() > 1) {
                	transactions.remove(0);
                    transactions.add(0, new Transaction(powRis));
                }
                
                System.out.println("Transaction attached to the Tangle!\n");
                System.out.println(transactions.get(0));
            } catch (ArgumentException ex) {
                Logger.getLogger(Promotion.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            //Other transactions
            //Set branch transaction to the trunk transaction value founded
            // Set trunk transaction to the hash of the next transaction in the bundle
            for(int i = 1; i < transactions.size(); i++) {
                transactions.get(i).setBranchTransaction(trunkTransaction);
                transactions.get(i).setTrunkTransaction(transactions.get(i-1).getHash());
                transactions.get(i).setAttachmentTimestamp(timestamp.getTime());

                trytes = transactions.get(i).toTrytes();
                //Proof of Work
                powRis = pow.performPoW(trytes, minMagnitudeWeight);
                transactions.remove(i);
                transactions.add(i, new Transaction(powRis));
                try {
                    iota.broadcastAndStore(powRis);
                    System.out.println("Transaction attached to the Tangle!\n");
                    System.out.println(transactions.get(i));
                } catch (ArgumentException ex) {
                    Logger.getLogger(Promotion.class.getName()).log(Level.SEVERE, null, ex);

                }
            }
            System.out.println("All transactions have been attached to the Tangle!");
        } else if(command.equals("-h")) {
            System.out.println("To promote a transaction: java -jar Promotion.jar -p <timesToPromote> <tailTransaction>\n");
            System.out.println("To reattach a transaction: java -jar Promotion.jar -r <tailTransaction>");
        }
        
        
    }
}
