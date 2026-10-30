from pathlib import Path
from io import BytesIO
from copy import deepcopy
import json
from pypdf import PdfReader, PdfWriter
from pypdf.generic import ContentStream, NameObject
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT=Path('C:/workspace/JustHumanResource')
BUILD=ROOT/'tmp/pdfs/justhr-deck'
SOURCE=BUILD/'original.pdf'
TARGET=ROOT/'doc/JUSTHR_Buyer_Sales_Deck.pdf'
if not SOURCE.exists(): SOURCE.write_bytes(TARGET.read_bytes())
r=PdfReader(SOURCE)
W,H=960.009449,540
NAVY='#101a33'; DARK='#10182c'; BLUE='#3859f0'; MUTED='#647697'; LIGHT='#b8c8eb'
pdfmetrics.registerFont(TTFont('Inter','C:/Windows/Fonts/Inter-Regular-slnt=0.ttf'))
pdfmetrics.registerFont(TTFont('Inter-Bold','C:/Windows/Fonts/Inter-Bold-slnt=0.ttf'))
checks=[]
def txt(c,text,x,top,size=14,color=NAVY,bold=False,width=None,leading=None):
    font='Inter-Bold' if bold else 'Inter'
    lines=text.split('\n')
    for n,line in enumerate(lines):
        measured=pdfmetrics.stringWidth(line,font,size)
        if width is not None:
            assert measured<=width+0.1,(text,measured,width)
        c.setFillColor(color);c.setFont(font,size)
        c.drawString(x,H-top-size*.82-n*(leading or size*1.25),line)
        checks.append({'text':line,'x':x,'top':top+n*(leading or size*1.25),'width':measured,'size':size})
def overlay(page,draw):
    b=BytesIO();c=canvas.Canvas(b,pagesize=(W,H));draw(c);c.save();b.seek(0)
    page.merge_page(PdfReader(b).pages[0])
    return page

def remove_text(page,predicate):
    stream=ContentStream(page.get_contents(),r)
    out=[]; group=None;pos=(0,0)
    for args,op in stream.operations:
        if op==b'BT': group=[(args,op)];pos=(0,0)
        elif group is not None:
            group.append((args,op))
            if op in (b'Td',b'TD'): pos=(float(args[0]),H-float(args[1]))
            elif op==b'Tm':pos=(float(args[4]),H-float(args[5]))
            if op==b'ET':
                if not predicate(*pos):out.extend(group)
                group=None
        else:out.append((args,op))
    assert group is None
    stream.operations=out
    page[NameObject('/Contents')]=stream
    return page

def blank(index):
    p=deepcopy(r.pages[index]);return remove_text(p,lambda x,t:t>65)
def common(c,kicker,title,subtitle=None):
    txt(c,kicker,52,78,10,BLUE,True,width=850)
    txt(c,title,52,112,34,NAVY,True,width=855,leading=41)
    if subtitle:txt(c,subtitle,52,204,14,MUTED,width=852)
def footer(c,num):
    txt(c,'JUSTHR  /  A JUSTJAVA TECHNOLOGIES PRODUCT',47.5,516,7.5,'#98a8c3',width=700)
    txt(c,f'{num:02}',899,516,7.5,'#98a8c3',width=24)

def promotion():
    p=blank(10)
    def draw(c):
        common(c,'CAREER GROWTH / PROMOTION MANAGEMENT','Every promotion has a clear path\nfrom proposal to payroll.','Support career growth with eligibility checks and an accountable approval process.')
        cards=[('ELIGIBILITY','Appraisal and time-in-role checks\nbefore a request progresses.'),('APPROVALS','Staged decisions, reminders\nand escalation for overdue reviews.'),('DOCUMENTS','Required evidence and generated\npromotion and salary letters.'),('IMPLEMENTATION','Approved role changes update\nemployee position history.'),('PAYROLL HANDOFF','Track salary changes through\nacknowledgement and application.'),('HR VISIBILITY','Dedicated work areas, request\nsearch and promotion reports.')]
        for i,(label,body) in enumerate(cards):
            x=[86.4,387.3,688.3][i%3];t=252.4+(i//3)*113
            txt(c,label,x,t,11.5,BLUE,True,width=230)
            txt(c,body,x,t+29,12.3,NAVY,width=230,leading=15.3)
    return overlay(p,draw)
def hiring():
    p=blank(1)
    def draw(c):
        common(c,'RECRUITMENT / AI-POWERED SHORTLISTING','AI-powered shortlisting.\nRecruiters retain the decision.')
        rows=[('01','AI-powered shortlisting','Match readable candidate documents to job\nrequirements, with scores and evidence gaps.'),('02','Recruiter review','Recruiters accept or override recommendations,\nwith comments and recorded reasons.'),('03','Hiring and onboarding','Schedule interviews, manage offers and carry\naccepted-offer details into employee onboarding.')]
        for i,(num,title,body) in enumerate(rows):
            t=260+i*72
            txt(c,num,55.4,t,13,BLUE,True,width=40)
            txt(c,title,115.2,t,18,NAVY,True,width=380)
            txt(c,body,516.2,t,13.5,MUTED,width=385,leading=17)
        txt(c,'Shortlisting uses document evidence matching. Recruiters review recommendations before proceeding.',52,474,10,MUTED,width=850)
    return overlay(p,draw)
def appraisal():
    p=blank(13)
    def draw(c):
        common(c,'PERFORMANCE / STANDARD AND SCORECARD APPRAISAL','Performance reviews with\nthe structure your teams need.')
        cards=[('STANDARD (FLAT) APPRAISAL','Review KPI results, self-assessments\nand manager scores in a clear appraisal.'),('BALANCED SCORECARD APPRAISAL','Structure weighted KPIs by perspective,\nobjective and indicator.'),('EMPLOYEE AND MANAGER REVIEW','Use scoring rubrics, evidence and comments\nto support employee and manager reviews.'),('DEVELOPMENT AND PROMOTION','Use final scores and perspective summaries\nto guide development and promotion reviews.')]
        for i,(label,body) in enumerate(cards):
            x=[72.7,515.5][i%2];t=248+(i//2)*118.8
            txt(c,label,x,t,11,BLUE,True,width=390)
            txt(c,body,x,t+28,13.5,NAVY,width=390,leading=17)
        txt(c,'Published templates and Excel imports support repeatable scorecard setup.',52,474,11,MUTED,width=850)
    return overlay(p,draw)

def operations():
    p=blank(10)
    def draw(c):
        common(c,'EMPLOYEE OPERATIONS / MORE DEPTH, LESS CHASING','The details that protect\nyour people operations.','Keep loan obligations, exit readiness and collaboration in the working record.')
        cards=[('LOAN PRODUCTS','Set lending terms and review\nemployee applications.'),('PAYROLL REPAYMENTS','Connect repayment schedules\nto payroll deductions.'),('EXIT CLEARANCE','Track required documents\nand outstanding asset returns.'),('FINAL SETTLEMENT','Calculate exit packages and\npost approved settlements to payroll.'),('EXIT READINESS','Check outstanding obligations\nbefore finalising an employee exit.'),('MEETINGS','Manage invitations and provider\nlinks alongside team communication.')]
        for i,(label,body) in enumerate(cards):
            x=[86.4,387.3,688.3][i%3];t=252.4+(i//3)*113
            txt(c,label,x,t,11.5,BLUE,True,width=230)
            txt(c,body,x,t+29,12.3,NAVY,width=230,leading=15.3)
        txt(c,'Meeting providers require the relevant account connection and configuration.',52,473,10,MUTED,width=850)
    return overlay(p,draw)

pages=[]
for i,source in enumerate(r.pages):
    p=deepcopy(source)
    # Keep source graphics and images intact. Change only selected text objects.
    if i==0:
        remove_text(p,lambda x,t:x<490 and 160<t<340)
        overlay(p,lambda c:(txt(c,'People decisions.\nPayroll clarity.',52.6,171,39,'#ffffff',True,width=430,leading=49),txt(c,'One connected HR platform.',56.1,312,21,LIGHT,width=420)))
    elif i==13:
        remove_text(p,lambda x,t:(x>=510 and 388<t<435) or (x<500 and 388<t<435))
        overlay(p,lambda c:(txt(c,'Connected hiring, promotions and employee services,\nwith records that stay close to the work.',72.7,395,13.5,NAVY,width=390,leading=17),txt(c,'Role permissions, salary visibility controls\nand approval history for a focused evaluation.',515.5,395,13.5,NAVY,width=390,leading=17)))
    elif i==14:
        remove_text(p,lambda x,t:(x>350 and 275<t<440))
        overlay(p,lambda c:(txt(c,'A payroll sample plus a hiring, promotion\nor employee-request workflow.',364.3,282,13,MUTED,width=510,leading=16),txt(c,'Agree checks for payroll accuracy, approval time\nand ease of access to employee records.',364.3,348,13,MUTED,width=510,leading=16),txt(c,'Review the evidence with HR, finance and IT\nbefore deciding on a wider rollout.',364.3,414,13,MUTED,width=510,leading=16)))
    elif i==15:
        remove_text(p,lambda x,t:160<t<390 or 435<t<470)
        overlay(p,lambda c:(txt(c,'Your next people decision,\nwith a clearer view.',52.5,174,40,'#ffffff',True,width=850,leading=50),txt(c,'Explore payroll, promotions and the employee journey\nthrough a demonstration built around your business.',55.4,335,19,'#d8e1f2',width=835,leading=24),txt(c,'Request a tailored JUSTHR demonstration',73.4,442,17,'#ffffff',True,width=790)))
    pages.append(p)
    if i==7:pages.extend([appraisal(),promotion()])
    if i==10:pages.extend([hiring(),operations()])

w=PdfWriter()
for j,p in enumerate(pages):
    if j not in (0,len(pages)-1):
        remove_text(p,lambda x,t:t>500 and x>880)
        # New slides had their old footer removed with all text.
        is_new=j in (8,9,13,14)
        if is_new:overlay(p,lambda c,n=j+1:footer(c,n))
        else:overlay(p,lambda c,n=j+1:txt(c,f'{n:02}',899,516,7.5,'#98a8c3',width=24))
    w.add_page(p)
w.add_metadata({'/Title':'JUSTHR - People, Payroll and Operations','/Author':'JUSTJAVA Technologies','/Subject':'Buyer sales presentation','/Keywords':'JUSTHR, payroll, HR, promotions, recruitment, onboarding, employee operations'})
# Keep original alongside the source for recovery, and replace only after successful export.
backup=ROOT/'doc/JUSTHR_Buyer_Sales_Deck.original.pdf'
if not backup.exists():backup.write_bytes(SOURCE.read_bytes())
draft=BUILD/'JUSTHR_Buyer_Sales_Deck.revised.pdf'
with draft.open('wb') as f:w.write(f)
check=PdfReader(draft)
assert len(check.pages)==20
for word in ['PROMOTION MANAGEMENT','PAYROLL REPAYMENTS','EXIT CLEARANCE','onboarding','AI-powered shortlisting','STANDARD (FLAT) APPRAISAL','BALANCED SCORECARD APPRAISAL']:
    assert word in '\n'.join(p.extract_text() for p in check.pages),word
assert all(abs(float(p.mediabox.width)-float(r.pages[0].mediabox.width))<0.1 for p in check.pages)
(BUILD/'text-fit-checks.json').write_text(json.dumps(checks,indent=2),encoding='utf-8')
TARGET.write_bytes(draft.read_bytes())
print('Created',TARGET,'with',len(check.pages),'pages. Original preserved at',backup)

