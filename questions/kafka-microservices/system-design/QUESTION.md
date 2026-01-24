# Kafka System Design Question

**Topic:** Kafka  
**Time Limit:** 15 minutes

---

## Problem Statement

You are tasked with designing a high-throughput, fault-tolerant data ingestion and processing pipeline using Apache Kafka. The system must support sustained traffic of over **100 million messages per hour**. Kafka topics are partitioned, and consumer groups are used to parallelize processing across multiple instances.

## Task

Describe in detail how you would design this system to ensure **reliability, scalability, and minimal data loss or processing delay** in the event that one or more consumer instances fail unexpectedly.

## Specific Aspects to Address

Your response must explicitly cover these five points:

1. **Kafka's message retention policies and how they support fault tolerance**
2. **Consumer group dynamics, including partition assignment and rebalancing behavior**
3. **Consumer offset management, including commit strategies and failure recovery**
4. **The role of replication factor in ensuring durability and availability**
5. **Autoscaling strategies, including how you would scale consumers up or down in response to changing load**

---

**Note:** This is a system design question requiring a comprehensive architectural approach covering all aspects of Kafka's fault tolerance and scalability mechanisms.
