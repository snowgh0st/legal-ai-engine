import { SignIn } from "@clerk/nextjs";

export default function SignInPage() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-slate-950">
      <SignIn 
        appearance={{
          elements: {
            card: "bg-slate-900 border border-slate-800 text-slate-100",
          }
        }}
      />
    </div>
  );
}