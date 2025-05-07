package main

import (
	"fmt"
	"github.com/gosnmp/gosnmp"
	"time"
)

// Perform an SNMP check for a specific IP and credential
func performSNMPCheck(ip string, credential Credential) SNMPCheckResult {
	result := SNMPCheckResult{
		CredentialID: credential.ID,
		Success:      false,
	}

	// Only proceed with SNMP check if system_type is "snmp"
	if credential.SystemType != "SNMP_V2C" {
		result.ErrorMessage = fmt.Sprintf("Credential system type '%s' is not supported for SNMP check", credential.SystemType)
		return result
	}

	// Initialize SNMP connection
	snmp := &gosnmp.GoSNMP{
		Target:    ip,
		Port:      161,
		Community: credential.Community,
		Version:   gosnmp.Version2c,
		Timeout:   time.Duration(2) * time.Second, // Short timeout for quick check
		Retries:   1,                              // Single retry for speed
	}

	startTime := time.Now()

	// Try to connect to the SNMP service
	err := snmp.Connect()
	if err != nil {
		result.ErrorMessage = fmt.Sprintf("Connect error: %v", err)
		return result
	}
	defer snmp.Conn.Close()

	// Query for the sysDescr OID (1.3.6.1.2.1.1.1.0)
	oids := []string{"1.3.6.1.2.1.1.1.0"} // sysDescr OID

	pdu, err := snmp.Get(oids)
	if err != nil {
		result.ErrorMessage = fmt.Sprintf("Get error: %v", err)
		return result
	}

	// SNMP connection is successful, calculate response time
	endTime := time.Now()
	result.ResponseTime = endTime.Sub(startTime).Milliseconds()

	// Check if sysDescr OID is present
	if len(pdu.Variables) > 0 {
		// If sysDescr is present, consider SNMP as successful
		result.Success = true
	} else {
		result.ErrorMessage = "sysDescr OID not found"
	}

	return result
}

func realSNMPFetch(ip, oid string, port int) string {
	params := &gosnmp.GoSNMP{
		Target:    ip,
		Port:      uint16(port),
		Community: "public", // Update this if your SNMP device uses a different community string
		Version:   gosnmp.Version2c,
		Timeout:   time.Duration(2) * time.Second,
		Retries:   1,
	}

	err := params.Connect()
	if err != nil {
		return "Connection error: " + err.Error()
	}
	defer params.Conn.Close()

	result, err := params.Get([]string{oid}) // Get single OID
	if err != nil {
		return "SNMP Get error: " + err.Error()
	}

	if len(result.Variables) > 0 {
		return fmt.Sprintf("%v", result.Variables[0].Value)
	}

	return "No result returned"
}
