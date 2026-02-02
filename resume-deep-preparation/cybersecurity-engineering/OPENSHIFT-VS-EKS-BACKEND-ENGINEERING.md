# OpenShift vs EKS – Quick Comparison

| Aspect | OpenShift | EKS |
|--------|-----------|-----|
| **Vendor** | Red Hat (IBM) | AWS |
| **Base** | Kubernetes + enterprise add-ons | Upstream Kubernetes (AWS-managed) |
| **Deployment** | Cloud, on-prem, hybrid | AWS; EKS Anywhere for on-prem |
| **Build** | S2I, BuildConfig, in-cluster registry | External CI/CD → ECR |
| **Security** | Restrictive (SCC, non-root by default) | Lenient; you configure RBAC, pod security |
| **Ingress** | Routes (built-in) | ALB/NLB Ingress Controller |
| **AWS integration** | Optional (ROSA) | Native (IRSA, Secrets Manager, etc.) |
| **Console** | Rich web UI | Minimal; CLI-centric |
| **When to use** | Hybrid, regulated, on-prem | AWS-first, heavy use of AWS services |

---

## Backend Engineer Scope (App Deployment)

| Your scope | OpenShift | EKS |
|------------|-----------|-----|
| Write manifests | Deployment, Service, ConfigMap, Secret | Same |
| Define env vars, probes | Same | Same |
| Mount config/secrets into pods | Same | Same |
| Expose your app | Route | Ingress (ALB) for your app |
| Build image | Often S2I (cluster builds) | Usually CI/CD pushes to ECR |
| Deploy | `oc apply` / GitOps | `kubectl apply` / GitOps |

**What you usually don't touch (cluster/platform team):**
- Creating/updating the EKS cluster
- Node groups, scaling, VPC/CNI
- IRSA, IAM roles for the cluster
- ALB Ingress Controller installation
