export type MagicLinkFlow = {
  flowId: string;
  email: string;
  status: "pending" | "verified" | "expired";
  sessionToken?: string;
  user?: any;
  createdAt: number;
};

// In-memory flow store with automatic TTL expiration (15 minutes)
const flows = new Map<string, MagicLinkFlow>();
const FLOW_TTL_MS = 15 * 60 * 1000;

// Periodic cleanup of expired flows every 5 minutes
setInterval(() => {
  const now = Date.now();
  for (const [id, flow] of flows.entries()) {
    if (now - flow.createdAt > FLOW_TTL_MS) {
      flows.delete(id);
    }
  }
}, 5 * 60 * 1000).unref?.();

export function registerFlow(flowId: string, email: string): MagicLinkFlow {
  const flow: MagicLinkFlow = {
    flowId,
    email,
    status: "pending",
    createdAt: Date.now(),
  };
  flows.set(flowId, flow);
  return flow;
}

export function completeFlow(flowId: string, sessionToken: string, user?: any): boolean {
  const flow = flows.get(flowId);
  if (!flow) return false;

  flow.status = "verified";
  flow.sessionToken = sessionToken;
  flow.user = user;
  return true;
}

export function getFlow(flowId: string): MagicLinkFlow | undefined {
  const flow = flows.get(flowId);
  if (!flow) return undefined;

  if (Date.now() - flow.createdAt > FLOW_TTL_MS) {
    flows.delete(flowId);
    return undefined;
  }
  return flow;
}

export function isFlowVerified(flowId: string): boolean {
  const flow = getFlow(flowId);
  return flow?.status === "verified";
}

export function consumeFlow(flowId: string): MagicLinkFlow | undefined {
  const flow = getFlow(flowId);
  if (flow && flow.status === "verified") {
    // Keep for a short 30-second grace window in case of retry, then delete
    setTimeout(() => flows.delete(flowId), 30000).unref?.();
  }
  return flow;
}
