package main

import (
	"encoding/json"
	"fmt"
	"os"
	"sync"
)

func main() {
	// Check command line arguments
	if len(os.Args) != 3 {
		fmt.Fprintf(os.Stderr, "Usage: %s <input_file> <output_file>\n", os.Args[0])
		os.Exit(1)
	}

	inputFile := os.Args[1]
	outputFile := os.Args[2]

	// Read input JSON
	inputData, err := os.ReadFile(inputFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error reading input file: %v\n", err)
		os.Exit(1)
	}

	var input Input
	if err := json.Unmarshal(inputData, &input); err != nil {
		fmt.Fprintf(os.Stderr, "Error parsing input JSON: %v\n", err)
		os.Exit(1)
	}

	if input.MethodType == "SNMP_CHECK" {
		// Perform SNMP checks for each IP and credential combination
		var wg sync.WaitGroup
		var mutex sync.Mutex
		var results []IPResult

		for _, ip := range input.IPs {
			var ipResult IPResult
			ipResult.IP = ip

			for _, credential := range input.Credentials {
				wg.Add(1)
				go func(ip string, cred Credential) {
					defer wg.Done()

					result := performSNMPCheck(ip, cred)

					// Lock and append the result to avoid race conditions
					mutex.Lock()
					ipResult.Credentials = append(ipResult.Credentials, result)
					mutex.Unlock()
				}(ip, credential)
			}

			// Append results after processing all credentials for this IP
			wg.Wait()
			results = append(results, ipResult)
		}

		// Create output structure
		output := Result{
			Results: results,
		}

		// Write output JSON
		outputData, err := json.MarshalIndent(output, "", "  ")
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error creating output JSON: %v\n", err)
			os.Exit(1)
		}

		if err := os.WriteFile(outputFile, outputData, 0644); err != nil {
			fmt.Fprintf(os.Stderr, "Error writing output file: %v\n", err)
			os.Exit(1)
		}

	}

	if input.MethodType == "SNMP_GET" {

		results := make(map[string]string)
		var mu sync.Mutex
		var wg sync.WaitGroup

		for _, ip := range input.IPs {

			for _, metric := range input.SnmpMetrics {
				wg.Add(1)

				go func(ip, metric string, port int) {
					defer wg.Done()

					value := realSNMPFetch(ip, metric, port)

					mu.Lock()
					results[metric] = value
					mu.Unlock()
				}(ip, metric, input.Port)
			}
		}

		wg.Wait()

		output := SNMPResult{
			IP:      input.IPs[0],
			Results: results}

		// Write output JSON
		outputData, err := json.MarshalIndent(output, "", "  ")
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error creating output JSON: %v\n", err)
			os.Exit(1)
		}

		if err := os.WriteFile(outputFile, outputData, 0644); err != nil {
			fmt.Fprintf(os.Stderr, "Error writing output file: %v\n", err)
			os.Exit(1)
		}
	}

}
