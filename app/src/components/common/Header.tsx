import { Shield as IconShield } from 'lucide-react';

export default function Header() {
    return (
        <header className="bg-[#ECF0FF] p-6">
            <div className="flex flex-col items-start">
                <span className="flex items-center gap-2 text-[#0051A2] font-bold px-4 py-2 rounded-md text-lg">
                    <IconShield className="w-5 h-5"/>
                        ERENO-UI
                </span>
                <h2 className="text-[#007AF0] font-bold  mt-4">
                    Dataset Generation System with cyberattacks in GOOSE protocol
                </h2>
            </div>
        </header>
    )
}