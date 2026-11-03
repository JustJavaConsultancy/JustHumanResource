from pathlib import Path
p=Path('tmp/pdfs/justhr-deck/improve_deck.py')
s=p.read_text(encoding='utf-8-sig')
s=s.replace("common(c,'HIRING / RECRUITMENT AND ONBOARDING','An accepted offer becomes\\na coordinated employee start.')", "common(c,'RECRUITMENT / AI-POWERED SHORTLISTING','AI-powered shortlisting.\\nRecruiters retain the decision.')")
a=s.index("        rows=[('01','Recruit with evidence'");b=s.index('        for i,(num,title,body)',a)
s=s[:a]+"""        rows=[('01','AI-powered shortlisting','Match readable candidate documents to job\\nrequirements, with scores and evidence gaps.'),('02','Recruiter review','Recruiters accept or override recommendations,\\nwith comments and recorded reasons.'),('03','Hiring and onboarding','Schedule interviews, manage offers and carry\\naccepted-offer details into employee onboarding.')]
"""+s[b:]
a=s.index('def hiring():');b=s.index('def operations():',a)
segment=s[a:b].replace('    return overlay(p,draw)', "        txt(c,'Shortlisting uses document evidence matching. Recruiters review recommendations before proceeding.',52,474,10,MUTED,width=850)\n    return overlay(p,draw)")
s=s[:a]+segment+s[b:]
a=s.index('def operations():')
s=s[:a]+'''def appraisal():
    p=blank(13)
    def draw(c):
        common(c,'PERFORMANCE / STANDARD AND SCORECARD APPRAISAL','Performance reviews with\\nthe structure your teams need.')
        cards=[('STANDARD (FLAT) APPRAISAL','Review KPI results, self-assessments\\nand manager scores in a clear appraisal.'),('BALANCED SCORECARD APPRAISAL','Structure weighted KPIs by perspective,\\nobjective and indicator.'),('EMPLOYEE AND MANAGER REVIEW','Use scoring rubrics, evidence and comments\\nto support employee and manager reviews.'),('DEVELOPMENT AND PROMOTION','Use final scores and perspective summaries\\nto guide development and promotion reviews.')]
        for i,(label,body) in enumerate(cards):
            x=[72.7,515.5][i%2];t=248+(i//2)*118.8
            txt(c,label,x,t,11,BLUE,True,width=390)
            txt(c,body,x,t+28,13.5,NAVY,width=390,leading=17)
        txt(c,'Published templates and Excel imports support repeatable scorecard setup.',52,474,11,MUTED,width=850)
    return overlay(p,draw)

'''+s[a:]
s=s.replace('if i==7:pages.append(promotion())','if i==7:pages.extend([appraisal(),promotion()])')
s=s.replace('is_new=j in (8,12,13)','is_new=j in (8,9,13,14)')
s=s.replace('assert len(check.pages)==19','assert len(check.pages)==20')
s=s.replace("'onboarding','recruiter review'", "'onboarding','AI-powered shortlisting','STANDARD (FLAT) APPRAISAL','BALANCED SCORECARD APPRAISAL'")
p.write_text(s,encoding='utf-8',newline='\n')
