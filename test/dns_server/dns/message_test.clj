(ns dns-server.dns.message-test
   (:require [clojure.test :as t]
             [dns-server.dns.message :as sut]))

;; Test for parse-16-bit-value helper function
(t/deftest test-parse-16-bit-value
  (t/testing "parse-16-bit-value creates a function that extracts a 16-bit value"
    (let [bytes [0x12 0x34 0x56 0x78]
          result-fn (sut/parse-16-bit-value :test-key)
          [rest-bytes result] (result-fn bytes {})]
      (t/is (= [0x56 0x78] rest-bytes))
      (t/is (= 0x1234 (:test-key result))))))

;; Tests for the specific 16-bit value parsers
(t/deftest test-parse-count-fields
  (t/testing "Testing qdcount parser"
    (let [bytes [0x00 0x01 0x00 0x02]
          initial-header {}
          [rest-bytes result] (sut/parse-qdcount bytes initial-header)]
      (t/is (= [0x00 0x02] rest-bytes))
      (t/is (= {::sut/qdcount 1} result))))

  (t/testing "Testing ancount parser"
    (let [bytes [0x00 0x02 0x00 0x03]
          initial-header {}
          [rest-bytes result] (sut/parse-ancount bytes initial-header)]
      (t/is (= [0x00 0x03] rest-bytes))
      (t/is (= {::sut/ancount 2} result))))

  (t/testing "Testing nscount parser"
    (let [bytes [0x00 0x03 0x00 0x04]
          initial-header {}
          [rest-bytes result] (sut/parse-nscount bytes initial-header)]
      (t/is (= [0x00 0x04] rest-bytes))
      (t/is (= {::sut/nscount 3} result))))

  (t/testing "Testing arcount parser"
    (let [bytes [0x00 0x04]
          initial-header {}
          [rest-bytes result] (sut/parse-arcount bytes initial-header)]
      (t/is (= nil rest-bytes))
      (t/is (= 4 (::sut/arcount result))))))

;; Test for parse-flags function
(t/deftest test-parse-flags
  (t/testing "Extracts flag bits from two bytes"
    (let [bytes [2r11111111 2r11111111]
          header {}
          [rest-bytes result] (sut/parse-flags bytes header)]

      ;; Check that flags are correctly parsed
      (t/is (= nil rest-bytes))
      (t/is (= 1 (::sut/qr result)))
      (t/is (= 0xF (::sut/opcode result)))
      (t/is (= 1 (::sut/aa result)))
      (t/is (= 1 (::sut/tc result)))
      (t/is (= 1 (::sut/rd result)))
      (t/is (= 1 (::sut/ra result)))
      (t/is (= 7 (::sut/z result)))
      (t/is (= 0xF (::sut/rcode result))))))

;; Test for parse-identifier function
(t/deftest test-parse-identifier
  (t/testing "Extracts the identifier (ID) from the first two bytes"
    (let [;; Bytes: ID = 0x1234, then flags
          bytes [0x12 0x34 0x81 0x80]
          header {}
          [rest-bytes result] (sut/parse-identifier bytes header)]

      ;; Check ID is correctly extracted and rest bytes are passed along
      (t/is (= [0x81 0x80] rest-bytes))
      (t/is (= 0x1234 (::sut/id result))))))

;; Test for parse-header function
(t/deftest test-parse-header
  (t/testing "Full header parsing function"
    (let [;; ID: 0x1234 (4660)
          ;; Flags: 0x8180 (qr=1, opcode=0, aa=0, tc=0, rd=1, ra=1, z=0, rcode=0)
          ;; QDCOUNT: 0x0001 (1)
          ;; ANCOUNT: 0x0002 (2)
          ;; NSCOUNT: 0x0003 (3)
          ;; ARCOUNT: 0x0004 (4)
          bytes [0x12 0x34                ;; ID
                 0xFF 0xFF
                 0x00 0x01                ;; QDCOUNT
                 0x00 0x02                ;; ANCOUNT
                 0x00 0x03                ;; NSCOUNT
                 0x00 0x04]               ;; ARCOUNT

          header (sut/parse-header bytes)]

      ;; Check all fields are correctly parsed
      (t/is (= 0x1234 (::sut/id header)))
      (t/is (= 1 (::sut/qr header)))
      (t/is (= 0xF (::sut/opcode header)))
      (t/is (= 1 (::sut/aa header)))
      (t/is (= 1 (::sut/tc header)))
      (t/is (= 1 (::sut/rd header)))
      (t/is (= 1 (::sut/ra header)))
      (t/is (= 7 (::sut/z header)))
      (t/is (= 0xF (::sut/rcode header)))
      (t/is (= 1 (::sut/qdcount header)))
      (t/is (= 2 (::sut/ancount header)))
      (t/is (= 3 (::sut/nscount header)))
      (t/is (= 4 (::sut/arcount header)))))

  (t/testing "Parsing with insufficient bytes returns nil"
    (let [byte-array [0x12 0x34 0x81]] ;; Only 3 bytes, not enough for a complete header
      (t/is (nil? (sut/parse-header byte-array))))))

(t/deftest test-valid-header
  (t/testing "valid-header? validates against spec"
    (let [valid-header {::sut/id 1234
                        ::sut/qr 1
                        ::sut/opcode 0
                        ::sut/aa 0
                        ::sut/tc 0
                        ::sut/rd 0
                        ::sut/ra 0
                        ::sut/z 0
                        ::sut/rcode 0
                        ::sut/qdcount 1
                        ::sut/ancount 0
                        ::sut/nscount 0
                        ::sut/arcount 0}

          ;; Invalid header with missing fields
          missing-fields {:id 1234}

          ;; Invalid header with invalid ID (out of range)
          invalid-id (assoc valid-header ::sut/id 70000)

          ;; Invalid header with invalid qr (not 0 or 1)
          invalid-qr (assoc valid-header ::sut/qr 2)]

      (t/is (sut/valid-header? valid-header))
      (t/is (not (sut/valid-header? missing-fields)))
      (t/is (not (sut/valid-header? invalid-id)))
      (t/is (not (sut/valid-header? invalid-qr))))))
