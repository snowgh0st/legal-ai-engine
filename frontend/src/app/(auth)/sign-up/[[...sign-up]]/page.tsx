import { SignUp } from "@clerk/nextjs";

export default function SignUpPage() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-slate-950">
      <SignUp 
        appearance={{
          elements: {
            card: "bg-slate-900 border border-slate-800 text-slate-100",
          }
        }}
      />
    </div>
  );
}