(ns dns-server.dns.message
  (:require [clojure.spec.alpha :as s]))

;; DNS message header specs
(s/def ::id (s/int-in 0 65536))  ;; 16-bit unsigned integer (0-65535)
(s/def ::qr #{0 1})              ;; 1 bit: 0=query, 1=response
(s/def ::opcode (s/int-in 0 16)) ;; 4-bit field (0-15)
(s/def ::aa #{0 1})              ;; 1 bit: 0=not authoritative, 1=authoritative
(s/def ::tc #{0 1})              ;; 1 bit: 0=not truncated, 1=truncated
(s/def ::rd #{0 1})              ;; 1 bit: 0=recursion not desired, 1=recursion desired
(s/def ::ra #{0 1})              ;; 1 bit: 0=recursion not available, 1=recursion available
(s/def ::z (s/int-in 0 8))       ;; 3-bit field (0-7), reserved
(s/def ::rcode (s/int-in 0 16))  ;; 4-bit field (0-15)
(s/def ::qdcount (s/int-in 0 65536)) ;; 16-bit unsigned integer
(s/def ::ancount (s/int-in 0 65536)) ;; 16-bit unsigned integer
(s/def ::nscount (s/int-in 0 65536)) ;; 16-bit unsigned integer
(s/def ::arcount (s/int-in 0 65536)) ;; 16-bit unsigned integer

;; Complete DNS header spec
(s/def ::header
  (s/keys :req [::id ::qr ::opcode ::aa ::tc ::rd ::ra ::z ::rcode
                ::qdcount ::ancount ::nscount ::arcount]))

(defn parse-16-bit-value
  [key]
  (fn [bytes header]
    (let [[high low & rest] bytes
          v (bit-or (bit-shift-left high 8) low)]
      [rest (assoc header key v)])))

(def parse-arcount (parse-16-bit-value ::arcount))
(def parse-nscount (parse-16-bit-value ::nscount))
(def parse-ancount (parse-16-bit-value ::ancount))
(def parse-qdcount (parse-16-bit-value ::qdcount))

(defn parse-flags
  [bytes header]
  (try
    (let [[high low & rest] bytes
          qr     (-> high (bit-and 2r10000000) (bit-shift-right 7))
          opcode (-> high (bit-and 2r01111000) (bit-shift-right 3))
          aa     (-> high (bit-and 2r00000100) (bit-shift-right 2))
          tc     (-> high (bit-and 2r00000010) (bit-shift-right 1))
          rd     (-> high (bit-and 2r00000001))
          ra     (-> low (bit-and 2r10000000) (bit-shift-right 7))
          z      (-> low (bit-and 2r01110000) (bit-shift-right 4))
          rcode  (-> low (bit-and 2r00001111))]
       [rest (assoc header ::qr qr ::opcode opcode
                    ::aa aa ::tc tc ::rd rd ::ra ra
                    ::z z ::rcode rcode)])
    (catch java.lang.NullPointerException _ nil)))

(defn parse-identifier
  [bytes header]
  (let [[high low & rest] bytes
        identifier (bit-or (bit-shift-left high 8) low)]
    [rest (assoc header ::id identifier)]))

(def seq-parsing-functions
  [parse-identifier
   parse-flags
   parse-qdcount
   parse-ancount
   parse-nscount
   parse-arcount])

(defn parse-header
  [bytes]
  (loop [fns seq-parsing-functions
         bytes bytes
         header {}]
     (if-let [[fx & rest] fns]
       (do
        (let [[bytes header] (fx bytes header)]
          (if (some? header)
            (recur rest bytes header))))
       header)))

(defn valid-header?
  "Check if a header is valid according to the spec"
  [header]
  (s/valid? ::header header))
