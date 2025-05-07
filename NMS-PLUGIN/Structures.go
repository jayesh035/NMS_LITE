package main

// Input represents the JSON structure received from the Java application
type Input struct {
	MethodType  string       `json:"method_type"`
	IPs         []string     `json:"ips"`
	Credentials []Credential `json:"credentials"`
	SnmpMetrics []string     `json:"snmp_metrics"`
	Port        int          `json:"port"`
}

// Credential represents a credential object
type Credential struct {
	ID         int64  `json:"id"`
	SystemType string `json:"system_type"`
	Community  string `json:"community"`
}

// SNMPCheckResult represents the result of an SNMP check
type SNMPCheckResult struct {
	CredentialID int64  `json:"credential_id"`
	Success      bool   `json:"success"`
	ErrorMessage string `json:"error_message,omitempty"`
	ResponseTime int64  `json:"response_time_ms,omitempty"`
}

// IPResult represents the result for an IP, including all credential results
type IPResult struct {
	IP          string            `json:"ip"`
	Credentials []SNMPCheckResult `json:"credentials"`
}

// Result represents the final output JSON structure
type Result struct {
	Results []IPResult `json:"results"`
}
type SNMPResult struct {
	IP      string            `json:"ip"`
	Results map[string]string `json:"results"`
}

// Maps metric names to SNMP OIDs
var oidMap = map[string]string{
	"sysName":     ".1.3.6.1.2.1.1.5.0",
	"sysDescr":    ".1.3.6.1.2.1.1.1.0",
	"sysLocation": ".1.3.6.1.2.1.1.6.0",
}
